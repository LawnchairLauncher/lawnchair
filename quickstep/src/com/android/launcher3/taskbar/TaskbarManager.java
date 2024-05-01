/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.taskbar;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_DESKTOP_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_TASKBAR_PINNING;
import static com.android.launcher3.util.DisplayController.TASKBAR_NOT_DESTROYED_TAG;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.quickstep.util.SystemActionConstants.ACTION_SHOW_TASKBAR;
import static com.android.quickstep.util.SystemActionConstants.SYSTEM_ACTION_ID_TASKBAR;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.AllAppsActionManager;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.AssistUtils;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import java.io.PrintWriter;
import java.util.StringJoiner;

/**
 * Class to manage taskbar lifecycle
 */
public class TaskbarManager {
    private static final String TAG = "TaskbarManager";
    private static final boolean DEBUG = false;

    /**
     * All the configurations which do not initiate taskbar recreation.
     * This includes all the configurations defined in Launcher's manifest entry and
     * ActivityController#filterConfigChanges
     */
    private static final int SKIP_RECREATE_CONFIG_CHANGES = ActivityInfo.CONFIG_WINDOW_CONFIGURATION
            | ActivityInfo.CONFIG_KEYBOARD
            | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            | ActivityInfo.CONFIG_MCC
            | ActivityInfo.CONFIG_MNC
            | ActivityInfo.CONFIG_NAVIGATION
            | ActivityInfo.CONFIG_ORIENTATION
            | ActivityInfo.CONFIG_SCREEN_SIZE
            | ActivityInfo.CONFIG_SCREEN_LAYOUT
            | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    private final Context mContext;
    private final @Nullable Context mNavigationBarPanelContext;
    private WindowManager mWindowManager;
    private FrameLayout mTaskbarRootLayout;
    private boolean mAddedWindow;
    private final TaskbarNavButtonController mNavButtonController;
    private final ComponentCallbacks mComponentCallbacks;

    private final SimpleBroadcastReceiver mShutdownReceiver =
            new SimpleBroadcastReceiver(i -> destroyExistingTaskbar());

    // The source for this provider is set when Launcher is available
    // We use 'non-destroyable' version here so the original provider won't be destroyed
    // as it is tied to the activity lifecycle, not the taskbar lifecycle.
    // It's destruction/creation will be managed by the activity.
    private final ScopedUnfoldTransitionProgressProvider mUnfoldProgressProvider =
            new NonDestroyableScopedUnfoldTransitionProgressProvider();

    private TaskbarActivityContext mTaskbarActivityContext;
    private StatefulActivity mActivity;
    /**
     * Cache a copy here so we can initialize state whenever taskbar is recreated, since
     * this class does not get re-initialized w/ new taskbars.
     */
    private final TaskbarSharedState mSharedState = new TaskbarSharedState();

    /**
     * We use WindowManager's ComponentCallbacks() for internal UI changes (similar to an Activity)
     * which comes via a different channel
     */
    private final RecreationListener mRecreationListener = new RecreationListener();

    private class RecreationListener implements DisplayController.DisplayInfoChangeListener {
        @Override
        public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
            if ((flags & (CHANGE_DENSITY | CHANGE_NAVIGATION_MODE | CHANGE_DESKTOP_MODE
                    | CHANGE_TASKBAR_PINNING)) != 0) {
                recreateTaskbar();
            }
        }
    }
    private final SettingsCache.OnChangeListener mOnSettingsChangeListener = c -> recreateTaskbar();

    private boolean mUserUnlocked = false;

    private final SimpleBroadcastReceiver mTaskbarBroadcastReceiver =
            new SimpleBroadcastReceiver(this::showTaskbarFromBroadcast);

    private final AllAppsActionManager mAllAppsActionManager;

    private final Runnable mActivityOnDestroyCallback = new Runnable() {
        @Override
        public void run() {
            if (mActivity != null) {
                mActivity.removeOnDeviceProfileChangeListener(
                        mDebugActivityDeviceProfileChanged);
                Log.d(TASKBAR_NOT_DESTROYED_TAG,
                        "unregistering activity lifecycle callbacks from "
                                + "onActivityDestroyed.");
                mActivity.removeEventCallback(EVENT_DESTROYED, this);
            }
            mActivity = null;
            debugWhyTaskbarNotDestroyed("clearActivity");
            if (mTaskbarActivityContext != null) {
                mTaskbarActivityContext.setUIController(TaskbarUIController.DEFAULT);
            }
            mUnfoldProgressProvider.setSourceProvider(null);
        }
    };

    UnfoldTransitionProgressProvider.TransitionProgressListener mUnfoldTransitionProgressListener =
            new UnfoldTransitionProgressProvider.TransitionProgressListener() {
                @Override
                public void onTransitionStarted() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition started getting called.");
                }

                @Override
                public void onTransitionProgress(float progress) {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition progress : " + progress);
                }

                @Override
                public void onTransitionFinishing() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition finishing getting called.");

                }

                @Override
                public void onTransitionFinished() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition finished getting called.");

                }
            };

    @SuppressLint("WrongConstant")
    public TaskbarManager(
            Context context,
            AllAppsActionManager allAppsActionManager,
            TaskbarNavButtonCallbacks navCallbacks) {

        Display display =
                context.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY);
        mContext = context.createWindowContext(display,
                ENABLE_TASKBAR_NAVBAR_UNIFICATION ? TYPE_NAVIGATION_BAR : TYPE_NAVIGATION_BAR_PANEL,
                null);
        mAllAppsActionManager = allAppsActionManager;
        mNavigationBarPanelContext = ENABLE_TASKBAR_NAVBAR_UNIFICATION
                ? context.createWindowContext(display, TYPE_NAVIGATION_BAR_PANEL, null)
                : null;
        if (enableTaskbarNoRecreate()) {
            mWindowManager = mContext.getSystemService(WindowManager.class);
            mTaskbarRootLayout = new FrameLayout(mContext) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    // The motion events can be outside the view bounds of task bar, and hence
                    // manually dispatching them to the drag layer here.
                    if (mTaskbarActivityContext != null
                            && mTaskbarActivityContext.getDragLayer().isAttachedToWindow()) {
                        return mTaskbarActivityContext.getDragLayer().dispatchTouchEvent(ev);
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
        }
        mNavButtonController = new TaskbarNavButtonController(
                context,
                navCallbacks,
                SystemUiProxy.INSTANCE.get(mContext),
                new Handler(),
                AssistUtils.newInstance(mContext));
        mComponentCallbacks = new ComponentCallbacks() {
            private Configuration mOldConfig = mContext.getResources().getConfiguration();

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                Trace.instantForTrack(Trace.TRACE_TAG_APP, "TaskbarManager",
                        "onConfigurationChanged: " + newConfig);
                debugWhyTaskbarNotDestroyed(
                        "TaskbarManager#mComponentCallbacks.onConfigurationChanged: " + newConfig);
                DeviceProfile dp = mUserUnlocked
                        ? LauncherAppState.getIDP(mContext).getDeviceProfile(mContext)
                        : null;
                int configDiff = mOldConfig.diff(newConfig) & ~SKIP_RECREATE_CONFIG_CHANGES;

                if ((configDiff & ActivityInfo.CONFIG_UI_MODE) != 0) {
                    // Only recreate for theme changes, not other UI mode changes such as docking.
                    int oldUiNightMode = (mOldConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    int newUiNightMode = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    if (oldUiNightMode == newUiNightMode) {
                        configDiff &= ~ActivityInfo.CONFIG_UI_MODE;
                    }
                }

                debugWhyTaskbarNotDestroyed("ComponentCallbacks#onConfigurationChanged() "
                        + "configDiff=" + Configuration.configurationDiffToString(configDiff));
                if (configDiff != 0 || mTaskbarActivityContext == null) {
                    recreateTaskbar();
                } else {
                    // Config change might be handled without re-creating the taskbar
                    if (dp != null && !isTaskbarEnabled(dp)) {
                        destroyExistingTaskbar();
                    } else {
                        if (dp != null && isTaskbarEnabled(dp)) {
                            if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
                                // Re-initialize for screen size change? Should this be done
                                // by looking at screen-size change flag in configDiff in the
                                // block above?
                                recreateTaskbar();
                            } else {
                                mTaskbarActivityContext.updateDeviceProfile(dp);
                            }
                        }
                        mTaskbarActivityContext.onConfigurationChanged(configDiff);
                    }
                }
                mOldConfig = new Configuration(newConfig);
                // reset taskbar was pinned value, so we don't automatically unstash taskbar upon
                // user unfolding the device.
                mSharedState.setTaskbarWasPinned(false);
            }

            @Override
            public void onLowMemory() { }
        };
        SettingsCache.INSTANCE.get(mContext)
                .register(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mContext)
                .register(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        Log.d(TASKBAR_NOT_DESTROYED_TAG, "registering component callbacks from constructor.");
        mContext.registerComponentCallbacks(mComponentCallbacks);
        mShutdownReceiver.register(mContext, Intent.ACTION_SHUTDOWN);
        UI_HELPER_EXECUTOR.execute(() -> {
            mSharedState.taskbarSystemActionPendingIntent = PendingIntent.getBroadcast(
                    mContext,
                    SYSTEM_ACTION_ID_TASKBAR,
                    new Intent(ACTION_SHOW_TASKBAR).setPackage(mContext.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mContext.registerReceiver(
                    mTaskbarBroadcastReceiver,
                    new IntentFilter(ACTION_SHOW_TASKBAR),
                    RECEIVER_NOT_EXPORTED);
        });

        debugWhyTaskbarNotDestroyed("TaskbarManager created");
        recreateTaskbar();
    }

    private void destroyExistingTaskbar() {
        debugWhyTaskbarNotDestroyed("destroyExistingTaskbar: " + mTaskbarActivityContext);
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onDestroy();
            if (!ENABLE_TASKBAR_NAVBAR_UNIFICATION || enableTaskbarNoRecreate()) {
                mTaskbarActivityContext = null;
            }
        }
        DeviceProfile dp = mUserUnlocked ?
                LauncherAppState.getIDP(mContext).getDeviceProfile(mContext) : null;
        if (dp == null || !isTaskbarEnabled(dp)) {
            removeTaskbarRootViewFromWindow();
        }
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    private void showTaskbarFromBroadcast(Intent intent) {
        if (ACTION_SHOW_TASKBAR.equals(intent.getAction()) && mTaskbarActivityContext != null) {
            mTaskbarActivityContext.showTaskbarFromBroadcast();
        }
    }

    /**
     * Toggles All Apps for Taskbar or Launcher depending on the current state.
     */
    public void toggleAllApps() {
        if (mTaskbarActivityContext == null || mTaskbarActivityContext.canToggleHomeAllApps()) {
            // Home All Apps should be toggled from this class, because the controllers are not
            // initialized when Taskbar is disabled (i.e. TaskbarActivityContext is null).
            if (mActivity instanceof Launcher l) l.toggleAllAppsSearch();
        } else {
            mTaskbarActivityContext.toggleAllAppsSearch();
        }
    }

    /**
     * Displays a frame of the first Launcher reveal animation.
     *
     * This should be used to run a first Launcher reveal animation whose progress matches a swipe
     * progress.
     */
    public AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        return mTaskbarActivityContext == null
                ? null : mTaskbarActivityContext.createLauncherStartFromSuwAnim(duration);
    }

    /**
     * Called when the user is unlocked
     */
    public void onUserUnlocked() {
        mUserUnlocked = true;
        DisplayController.INSTANCE.get(mContext).addChangeListener(mRecreationListener);
        recreateTaskbar();
        addTaskbarRootViewToWindow();
    }

    /**
     * Sets a {@link StatefulActivity} to act as taskbar callback
     */
    public void setActivity(@NonNull StatefulActivity activity) {
        if (mActivity == activity) {
            return;
        }
        removeActivityCallbacksAndListeners();
        mActivity = activity;
        debugWhyTaskbarNotDestroyed("Set mActivity=" + mActivity);
        mActivity.addOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
        Log.d(TASKBAR_NOT_DESTROYED_TAG,
                "registering activity lifecycle callbacks from setActivity().");
        mActivity.addEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
        UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                getUnfoldTransitionProgressProviderForActivity(activity);
        if (unfoldTransitionProgressProvider != null) {
            unfoldTransitionProgressProvider.addCallback(mUnfoldTransitionProgressListener);
        }
        mUnfoldProgressProvider.setSourceProvider(unfoldTransitionProgressProvider);

        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setUIController(
                    createTaskbarUIControllerForActivity(mActivity));
        }
    }

    /**
     * Returns an {@link UnfoldTransitionProgressProvider} to use while the given StatefulActivity
     * is active.
     */
    private UnfoldTransitionProgressProvider getUnfoldTransitionProgressProviderForActivity(
            StatefulActivity activity) {
        if (!enableUnfoldStateAnimation()) {
            if (activity instanceof QuickstepLauncher ql) {
                return ql.getUnfoldTransitionProgressProvider();
            }
        } else {
            return SystemUiProxy.INSTANCE.get(mContext).getUnfoldTransitionProvider();
        }
        return null;
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForActivity(StatefulActivity activity) {
        if (activity instanceof QuickstepLauncher) {
            return new LauncherTaskbarUIController((QuickstepLauncher) activity);
        }
        if (activity instanceof RecentsActivity) {
            return new FallbackTaskbarUIController((RecentsActivity) activity);
        }
        return TaskbarUIController.DEFAULT;
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy an existing taskbar and create a new one.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    @VisibleForTesting
    public synchronized void recreateTaskbar() {
        Trace.beginSection("recreateTaskbar");
        try {
            DeviceProfile dp = mUserUnlocked ?
                LauncherAppState.getIDP(mContext).getDeviceProfile(mContext) : null;

            // All Apps action is unrelated to navbar unification, so we only need to check DP.
            final boolean isLargeScreenTaskbar = dp != null && dp.isTaskbarPresent;
            mAllAppsActionManager.setTaskbarPresent(isLargeScreenTaskbar);

            destroyExistingTaskbar();

            boolean isTaskbarEnabled = dp != null && isTaskbarEnabled(dp);
            debugWhyTaskbarNotDestroyed("recreateTaskbar: isTaskbarEnabled=" + isTaskbarEnabled
                + " [dp != null (i.e. mUserUnlocked)]=" + (dp != null)
                + " FLAG_HIDE_NAVBAR_WINDOW=" + ENABLE_TASKBAR_NAVBAR_UNIFICATION
                + " dp.isTaskbarPresent=" + (dp == null ? "null" : dp.isTaskbarPresent));
            if (!isTaskbarEnabled) {
                SystemUiProxy.INSTANCE.get(mContext)
                    .notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
                return;
            }

            if (enableTaskbarNoRecreate() || mTaskbarActivityContext == null) {
                mTaskbarActivityContext = new TaskbarActivityContext(mContext,
                        mNavigationBarPanelContext, dp, mNavButtonController,
                        mUnfoldProgressProvider);
            } else {
                mTaskbarActivityContext.updateDeviceProfile(dp);
            }
            mSharedState.startTaskbarVariantIsTransient =
                    DisplayController.isTransientTaskbar(mTaskbarActivityContext);
            mSharedState.allAppsVisible = mSharedState.allAppsVisible && isLargeScreenTaskbar;
            mTaskbarActivityContext.init(mSharedState);

            if (mActivity != null) {
                mTaskbarActivityContext.setUIController(
                    createTaskbarUIControllerForActivity(mActivity));
            }

            if (enableTaskbarNoRecreate()) {
                addTaskbarRootViewToWindow();
                mTaskbarRootLayout.removeAllViews();
                mTaskbarRootLayout.addView(mTaskbarActivityContext.getDragLayer());
                mTaskbarActivityContext.notifyUpdateLayoutParams();
            }
        } finally {
            Trace.endSection();
        }
    }

    public void onSystemUiFlagsChanged(int systemUiStateFlags) {
        if (DEBUG) {
            Log.d(TAG, "SysUI flags changed: " + formatFlagChange(systemUiStateFlags,
                    mSharedState.sysuiStateFlags, QuickStepContract::getSystemUiStateString));
        }
        mSharedState.sysuiStateFlags = systemUiStateFlags;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.updateSysuiStateFlags(systemUiStateFlags, false /* fromInit */);
        }
    }

    public void onLongPressHomeEnabled(boolean assistantLongPressEnabled) {
        if (mNavButtonController != null) {
            mNavButtonController.setAssistantLongPressEnabled(assistantLongPressEnabled);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mSharedState.setupUIVisible = isVisible;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setSetupUIVisible(isVisible);
        }
    }

    private static boolean isTaskbarEnabled(DeviceProfile deviceProfile) {
        return ENABLE_TASKBAR_NAVBAR_UNIFICATION || deviceProfile.isTaskbarPresent;
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onRotationProposal(rotation, isValid);
        }
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        mSharedState.disableNavBarDisplayId = displayId;
        mSharedState.disableNavBarState1 = state1;
        mSharedState.disableNavBarState2 = state2;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.disableNavBarElements(displayId, state1, state2, animate);
        }
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        mSharedState.systemBarAttrsDisplayId = displayId;
        mSharedState.systemBarAttrsBehavior = behavior;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onSystemBarAttributesChanged(displayId, behavior);
        }
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mSharedState.navButtonsDarkIntensity = darkIntensity;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onNavButtonsDarkIntensityChanged(darkIntensity);
        }
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        mSharedState.mLumaSamplingDisplayId = displayId;
        mSharedState.mIsLumaSamplingEnabled = enable;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onNavigationBarLumaSamplingEnabled(displayId, enable);
        }
    }

    private void removeActivityCallbacksAndListeners() {
        if (mActivity != null) {
            mActivity.removeOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
            Log.d(TASKBAR_NOT_DESTROYED_TAG,
                    "unregistering activity lifecycle callbacks from "
                            + "removeActivityCallbackAndListeners().");
            mActivity.removeEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                    getUnfoldTransitionProgressProviderForActivity(mActivity);
            if (unfoldTransitionProgressProvider != null) {
                unfoldTransitionProgressProvider.removeCallback(mUnfoldTransitionProgressListener);
            }
        }
    }

    /**
     * Called when the manager is no longer needed
     */
    public void destroy() {
        debugWhyTaskbarNotDestroyed("TaskbarManager#destroy()");
        removeActivityCallbacksAndListeners();
        UI_HELPER_EXECUTOR.execute(
                () -> mTaskbarBroadcastReceiver.unregisterReceiverSafely(mContext));
        destroyExistingTaskbar();
        removeTaskbarRootViewFromWindow();
        if (mUserUnlocked) {
            DisplayController.INSTANCE.get(mContext).removeChangeListener(mRecreationListener);
        }
        SettingsCache.INSTANCE.get(mContext)
                .unregister(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mContext)
                .unregister(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        Log.d(TASKBAR_NOT_DESTROYED_TAG, "unregistering component callbacks from destroy().");
        mContext.unregisterComponentCallbacks(mComponentCallbacks);
        mContext.unregisterReceiver(mShutdownReceiver);
    }

    public @Nullable TaskbarActivityContext getCurrentActivityContext() {
        return mTaskbarActivityContext;
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarManager:");
        if (mTaskbarActivityContext == null) {
            pw.println(prefix + "\tTaskbarActivityContext: null");
        } else {
            mTaskbarActivityContext.dumpLogs(prefix + "\t", pw);
        }
    }

    private void addTaskbarRootViewToWindow() {
        if (enableTaskbarNoRecreate() && !mAddedWindow && mTaskbarActivityContext != null) {
            mWindowManager.addView(mTaskbarRootLayout,
                    mTaskbarActivityContext.getWindowLayoutParams());
            mAddedWindow = true;
        }
    }

    private void removeTaskbarRootViewFromWindow() {
        if (enableTaskbarNoRecreate() && mAddedWindow) {
            mWindowManager.removeViewImmediate(mTaskbarRootLayout);
            mAddedWindow = false;
        }
    }

    /** Temp logs for b/254119092. */
    public void debugWhyTaskbarNotDestroyed(String debugReason) {
        StringJoiner log = new StringJoiner("\n");
        log.add(debugReason);

        boolean activityTaskbarPresent = mActivity != null
                && mActivity.getDeviceProfile().isTaskbarPresent;
        boolean contextTaskbarPresent = mUserUnlocked
                && LauncherAppState.getIDP(mContext).getDeviceProfile(mContext).isTaskbarPresent;
        if (activityTaskbarPresent == contextTaskbarPresent) {
            log.add("mActivity and mContext agree taskbarIsPresent=" + contextTaskbarPresent);
            Log.d(TASKBAR_NOT_DESTROYED_TAG, log.toString());
            return;
        }

        log.add("mActivity and mContext device profiles have different values, add more logs.");

        log.add("\tmActivity logs:");
        log.add("\t\tmActivity=" + mActivity);
        if (mActivity != null) {
            log.add("\t\tmActivity.getResources().getConfiguration()="
                    + mActivity.getResources().getConfiguration());
            log.add("\t\tmActivity.getDeviceProfile().isTaskbarPresent="
                    + activityTaskbarPresent);
        }
        log.add("\tmContext logs:");
        log.add("\t\tmContext=" + mContext);
        log.add("\t\tmContext.getResources().getConfiguration()="
                + mContext.getResources().getConfiguration());
        if (mUserUnlocked) {
            log.add("\t\tLauncherAppState.getIDP().getDeviceProfile(mContext).isTaskbarPresent="
                    + contextTaskbarPresent);
        } else {
            log.add("\t\tCouldn't get DeviceProfile because !mUserUnlocked");
        }

        Log.d(TASKBAR_NOT_DESTROYED_TAG, log.toString());
    }

    private final DeviceProfile.OnDeviceProfileChangeListener mDebugActivityDeviceProfileChanged =
            dp -> debugWhyTaskbarNotDestroyed("mActivity onDeviceProfileChanged");
}
