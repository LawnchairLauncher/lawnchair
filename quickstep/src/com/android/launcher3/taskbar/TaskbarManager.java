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
import static android.content.pm.PackageManager.FEATURE_PC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_KEY;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.util.DisplayController.TASKBAR_NOT_DESTROYED_TAG;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;
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

    public static final boolean FLAG_HIDE_NAVBAR_WINDOW =
            SystemProperties.getBoolean("persist.wm.debug.hide_navbar_window", false);

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    private final Context mContext;
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
    private final OnIDPChangeListener mIdpChangeListener = c -> recreateTaskbar();
    private final SettingsCache.OnChangeListener mOnSettingsChangeListener = c -> recreateTaskbar();

    private boolean mUserUnlocked = false;

    public static final int SYSTEM_ACTION_ID_TASKBAR = 499;

    /**
     * For Taskbar broadcast intent filter.
     */
    public static final String ACTION_SHOW_TASKBAR = "ACTION_SHOW_TASKBAR";

    private final SimpleBroadcastReceiver mTaskbarBroadcastReceiver =
            new SimpleBroadcastReceiver(this::showTaskbarFromBroadcast);

    private final SharedPreferences.OnSharedPreferenceChangeListener
            mTaskbarPinningPreferenceChangeListener = (sharedPreferences, key) -> {
                if (TASKBAR_PINNING_KEY.equals(key)) {
                    recreateTaskbar();
                }
            };

    private final ActivityLifecycleCallbacksAdapter mLifecycleCallbacks =
            new ActivityLifecycleCallbacksAdapter() {
                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (mActivity != activity) return;
                    if (mActivity != null) {
                        mActivity.removeOnDeviceProfileChangeListener(
                                mDebugActivityDeviceProfileChanged);
                        Log.d(TASKBAR_NOT_DESTROYED_TAG,
                                "unregistering activity lifecycle callbacks from "
                                        + "onActivityDestroyed.");
                        mActivity.unregisterActivityLifecycleCallbacks(this);
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
    public TaskbarManager(TouchInteractionService service) {
        Display display =
                service.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY);
        mContext = service.createWindowContext(display, TYPE_NAVIGATION_BAR_PANEL, null);
        mNavButtonController = new TaskbarNavButtonController(service,
                SystemUiProxy.INSTANCE.get(mContext), new Handler(),
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
                    if (dp != null && !isTaskbarPresent(dp)) {
                        destroyExistingTaskbar();
                    } else {
                        if (dp != null && isTaskbarPresent(dp)) {
                            if (FLAG_HIDE_NAVBAR_WINDOW) {
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
            LauncherPrefs.get(mContext).removeListener(mTaskbarPinningPreferenceChangeListener,
                    TASKBAR_PINNING);
            mTaskbarActivityContext.onDestroy();
            if (!FLAG_HIDE_NAVBAR_WINDOW) {
                mTaskbarActivityContext = null;
            }
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
     *
     * @param homeAllAppsIntent Intent used if Taskbar is not enabled or Launcher is resumed.
     */
    public void toggleAllApps(Intent homeAllAppsIntent) {
        if (mTaskbarActivityContext == null) {
            mContext.startActivity(homeAllAppsIntent);
            return;
        }

        if (mActivity != null && mActivity.isResumed() && !mActivity.isInState(OVERVIEW)) {
            mContext.startActivity(homeAllAppsIntent);
            return;
        }

        mTaskbarActivityContext.toggleAllApps();
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
        LauncherAppState.getIDP(mContext).addOnChangeListener(mIdpChangeListener);
        recreateTaskbar();
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
        mActivity.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
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
        if (activity instanceof QuickstepLauncher) {
            return ((QuickstepLauncher) activity).getUnfoldTransitionProgressProvider();
        }
        return null;
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForActivity(StatefulActivity activity) {
        if (activity instanceof QuickstepLauncher) {
            if (mTaskbarActivityContext.getPackageManager().hasSystemFeature(FEATURE_PC)) {
                return new DesktopTaskbarUIController((QuickstepLauncher) activity);
            }
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
    public void recreateTaskbar() {
        Trace.beginSection("recreateTaskbar");
        try {
            DeviceProfile dp = mUserUnlocked ?
                LauncherAppState.getIDP(mContext).getDeviceProfile(mContext) : null;

            destroyExistingTaskbar();

            boolean isTaskbarEnabled = dp != null && isTaskbarPresent(dp);
            debugWhyTaskbarNotDestroyed("recreateTaskbar: isTaskbarEnabled=" + isTaskbarEnabled
                + " [dp != null (i.e. mUserUnlocked)]=" + (dp != null)
                + " FLAG_HIDE_NAVBAR_WINDOW=" + FLAG_HIDE_NAVBAR_WINDOW
                + " dp.isTaskbarPresent=" + (dp == null ? "null" : dp.isTaskbarPresent));
            if (!isTaskbarEnabled) {
                SystemUiProxy.INSTANCE.get(mContext)
                    .notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
                return;
            }

            if (mTaskbarActivityContext == null) {
                mTaskbarActivityContext = new TaskbarActivityContext(mContext, dp,
                    mNavButtonController,
                    mUnfoldProgressProvider);
            } else {
                mTaskbarActivityContext.updateDeviceProfile(dp);
            }
            mTaskbarActivityContext.init(mSharedState);

            if (mActivity != null) {
                mTaskbarActivityContext.setUIController(
                    createTaskbarUIControllerForActivity(mActivity));
            }

            // We to wait until user unlocks the device to attach listener.
            LauncherPrefs.get(mContext).addListener(mTaskbarPinningPreferenceChangeListener,
                TASKBAR_PINNING);
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

    /**
     * @return {@code true} if provided device profile isn't a large screen profile
     *                      and we are using a single window for taskbar and navbar.
     */
    public static boolean isPhoneMode(DeviceProfile deviceProfile) {
        return TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW && deviceProfile.isPhone;
    }

    /**
     * @return {@code true} if {@link #isPhoneMode(DeviceProfile)} is true and we're using
     *                      3 button-nav
     */
    public static boolean isPhoneButtonNavMode(TaskbarActivityContext context) {
        return isPhoneMode(context.getDeviceProfile()) && context.isThreeButtonNav();
    }

    private boolean isTaskbarPresent(DeviceProfile deviceProfile) {
        return FLAG_HIDE_NAVBAR_WINDOW || deviceProfile.isTaskbarPresent;
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

    private void removeActivityCallbacksAndListeners() {
        if (mActivity != null) {
            mActivity.removeOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
            Log.d(TASKBAR_NOT_DESTROYED_TAG,
                    "unregistering activity lifecycle callbacks from "
                            + "removeActivityCallbackAndListeners().");
            mActivity.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
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
        if (mUserUnlocked) {
            LauncherAppState.getIDP(mContext).removeOnChangeListener(mIdpChangeListener);
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
