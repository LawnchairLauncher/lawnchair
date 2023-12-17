/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Launcher.INTENT_ACTION_ALL_APPS_TOGGLE;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TRACKPAD_GESTURE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;
import static com.android.quickstep.GestureState.DEFAULT_STATE;
import static com.android.quickstep.GestureState.TrackpadGestureType.getTrackpadGestureType;
import static com.android.quickstep.InputConsumer.TYPE_CURSOR_HOVER;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.FLAG_USING_OTHER_ACTIVITY_INPUT_CONSUMER;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_DOWN;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_MOVE;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_UP;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_UNFOLD_ANIMATION_FORWARDER;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_BACK_ANIMATION;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_BUBBLES;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_DRAG_AND_DROP;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_ONE_HANDED;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_PIP;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_RECENT_TASKS;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_SHELL_TRANSITIONS;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_SPLIT_SCREEN;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_STARTING_WINDOW;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.app.Service;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Region;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.uioverrides.flags.FlagsFactory;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer;
import com.android.quickstep.inputconsumers.AssistantInputConsumer;
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer;
import com.android.quickstep.inputconsumers.NavHandleLongPressInputConsumer;
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer;
import com.android.quickstep.inputconsumers.ProgressDelegateInputConsumer;
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer;
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer;
import com.android.quickstep.inputconsumers.SysUiOverlayInputConsumer;
import com.android.quickstep.inputconsumers.TaskbarUnstashInputConsumer;
import com.android.quickstep.inputconsumers.TrackpadStatusBarInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureLog.CompoundString;
import com.android.quickstep.util.AssistStateManager;
import com.android.quickstep.util.AssistUtils;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController;
import com.android.systemui.unfold.progress.IUnfoldAnimation;
import com.android.wm.shell.back.IBackAnimation;
import com.android.wm.shell.bubbles.IBubbles;
import com.android.wm.shell.desktopmode.IDesktopMode;
import com.android.wm.shell.draganddrop.IDragAndDrop;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.pip.IPip;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.startingsurface.IStartingWindow;
import com.android.wm.shell.transition.IShellTransitions;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Function;

import app.lawnchair.LawnchairApp;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.R)
public class TouchInteractionService extends Service {

    private static final String SUBSTRING_PREFIX = "; ";
    private static final String NEWLINE_PREFIX = "\n\t\t\t-> ";

    private static final String TAG = "TouchInteractionService";

    private static final String HAS_ENABLED_QUICKSTEP_ONCE = "launcher.has_enabled_quickstep_once";

    private final TISBinder mTISBinder = LawnchairApp.isRecentsEnabled() ? new TISBinder(this) : null;

    /**
     * Local IOverviewProxy implementation with some methods for local components
     */
    public static class TISBinder extends IOverviewProxy.Stub {

        private final WeakReference<TouchInteractionService> mTis;

        @Nullable
        private Runnable mOnOverviewTargetChangeListener = null;

        private TISBinder(TouchInteractionService tis) {
            mTis = new WeakReference<>(tis);
        }

        @BinderThread
        public void onInitialize(Bundle bundle) {
            ISystemUiProxy proxy = ISystemUiProxy.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            IPip pip = IPip.Stub.asInterface(bundle.getBinder(KEY_EXTRA_SHELL_PIP));
            IBubbles bubbles = IBubbles.Stub.asInterface(bundle.getBinder(KEY_EXTRA_SHELL_BUBBLES));
            ISplitScreen splitscreen = ISplitScreen.Stub.asInterface(bundle.getBinder(
                    KEY_EXTRA_SHELL_SPLIT_SCREEN));
            IOneHanded onehanded = IOneHanded.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_ONE_HANDED));
            IShellTransitions shellTransitions = IShellTransitions.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_SHELL_TRANSITIONS));
            IStartingWindow startingWindow = IStartingWindow.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_STARTING_WINDOW));
            ISysuiUnlockAnimationController launcherUnlockAnimationController = ISysuiUnlockAnimationController.Stub
                    .asInterface(
                            bundle.getBinder(KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER));
            IRecentTasks recentTasks = IRecentTasks.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_RECENT_TASKS));
            IBackAnimation backAnimation = IBackAnimation.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_BACK_ANIMATION));
            IDesktopMode desktopMode = IDesktopMode.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_DESKTOP_MODE));
            IUnfoldAnimation unfoldTransition = IUnfoldAnimation.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_UNFOLD_ANIMATION_FORWARDER));
            IDragAndDrop dragAndDrop = IDragAndDrop.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_DRAG_AND_DROP));
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                SystemUiProxy.INSTANCE.get(tis).setProxy(proxy, pip,
                        bubbles, splitscreen, onehanded, shellTransitions, startingWindow,
                        recentTasks, launcherUnlockAnimationController, backAnimation, desktopMode,
                        unfoldTransition, dragAndDrop);
                tis.initInputMonitor("TISBinder#onInitialize()");
                tis.preloadOverview(true /* fromInit */);
            }));
            sIsInitialized = true;
        }

        @BinderThread
        @Override
        public void onTaskbarToggled() {
            if (!FeatureFlags.ENABLE_KEYBOARD_TASKBAR_TOGGLE.get())
                return;
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                TaskbarActivityContext activityContext = tis.mTaskbarManager.getCurrentActivityContext();

                if (activityContext != null) {
                    activityContext.toggleTaskbarStash();
                }
            }));
        }

        @BinderThread
        public void onOverviewToggle() {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
            executeForTouchInteractionService(tis -> {
                // If currently screen pinning, do not enter overview
                if (tis.mDeviceState.isScreenPinningActive()) {
                    return;
                }
                TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                tis.mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_TOGGLE);
            });
        }

        @BinderThread
        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            executeForTouchInteractionService(tis -> {
                if (triggeredFromAltTab) {
                    TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                    tis.mOverviewCommandHelper.addCommand(
                            OverviewCommandHelper.TYPE_KEYBOARD_INPUT);
                } else {
                    tis.mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_SHOW);
                }
            });
        }

        @BinderThread
        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            executeForTouchInteractionService(tis -> {
                if (triggeredFromAltTab && !triggeredFromHomeKey) {
                    // onOverviewShownFromAltTab hides the overview and ends at the target app
                    tis.mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_HIDE);
                }
            });
        }

        @BinderThread
        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceState.setAssistantAvailable(available);
                tis.onAssistantVisibilityChanged();
                executeForTaskbarManager(taskbarManager -> taskbarManager
                        .onLongPressHomeEnabled(longPressHomeEnabled));
            }));
        }

        @BinderThread
        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceState.setAssistantVisibility(visibility);
                tis.onAssistantVisibilityChanged();
            }));
        }

        /**
         * Sent when the assistant has been invoked with the given type (defined in
         * AssistManager)
         * and should be shown. This method is used if
         * SystemUiProxy#setAssistantOverridesRequested
         * was previously called including this invocation type.
         */
        @Override
        public void onAssistantOverrideInvoked(int invocationType) {
            executeForTouchInteractionService(tis -> {
                if (!AssistUtils.newInstance(tis).tryStartAssistOverride(invocationType)) {
                    Log.w(TAG, "Failed to invoke Assist override");
                }
            });
        }

        @Override
        public void onNavigationBarSurface(SurfaceControl surface) {
            // TODO: implement
        }

        @BinderThread
        public void onSystemUiStateChanged(int stateFlags) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                int lastFlags = tis.mDeviceState.getSystemUiStateFlags();
                tis.mDeviceState.setSystemUiFlags(stateFlags);
                tis.onSystemUiFlagsChanged(lastFlags);
            }));
        }

        @BinderThread
        public void onActiveNavBarRegionChanges(Region region) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(
                    tis -> tis.mDeviceState.setDeferredGestureRegion(region)));
        }

        @BinderThread
        @Override
        public void enterStageSplitFromRunningApp(boolean leftOrTop) {
            executeForTouchInteractionService(tis -> {
                StatefulActivity activity = tis.mOverviewComponentObserver.getActivityInterface().getCreatedActivity();
                if (activity != null) {
                    activity.enterStageSplitFromRunningApp(leftOrTop);
                }
            });
        }

        /**
         * Preloads the Overview activity.
         * <p>
         * This method should only be used when the All Set page of the SUW is reached
         * to safely
         * preload the Launcher for the SUW first reveal.
         */
        public void preloadOverviewForSUWAllSet() {
            executeForTouchInteractionService(tis -> tis.preloadOverview(false, true));
        }

        @Override
        public void onRotationProposal(int rotation, boolean isValid) {
            executeForTaskbarManager(taskbarManager -> taskbarManager.onRotationProposal(rotation, isValid));
        }

        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {
            executeForTaskbarManager(
                    taskbarManager -> taskbarManager.disableNavBarElements(displayId, state1, state2, animate));
        }

        @Override
        public void onSystemBarAttributesChanged(int displayId, int behavior) {
            executeForTaskbarManager(
                    taskbarManager -> taskbarManager.onSystemBarAttributesChanged(displayId, behavior));
        }

        @Override
        public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
            executeForTaskbarManager(taskbarManager -> taskbarManager.onNavButtonsDarkIntensityChanged(darkIntensity));
        }

        private void executeForTouchInteractionService(
                @NonNull Consumer<TouchInteractionService> tisConsumer) {
            TouchInteractionService tis = mTis.get();
            if (tis == null)
                return;
            tisConsumer.accept(tis);
        }

        private void executeForTaskbarManager(
                @NonNull Consumer<TaskbarManager> taskbarManagerConsumer) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                TaskbarManager taskbarManager = tis.mTaskbarManager;
                if (taskbarManager == null)
                    return;
                taskbarManagerConsumer.accept(taskbarManager);
            }));
        }

        /**
         * Returns the {@link TaskbarManager}.
         * <p>
         * Returns {@code null} if TouchInteractionService is not connected
         */
        @Nullable
        public TaskbarManager getTaskbarManager() {
            TouchInteractionService tis = mTis.get();
            if (tis == null)
                return null;
            return tis.mTaskbarManager;
        }

        /**
         * Returns the {@link OverviewCommandHelper}.
         * <p>
         * Returns {@code null} if TouchInteractionService is not connected
         */
        @Nullable
        public OverviewCommandHelper getOverviewCommandHelper() {
            TouchInteractionService tis = mTis.get();
            if (tis == null)
                return null;
            return tis.mOverviewCommandHelper;
        }

        /**
         * Sets a proxy to bypass swipe up behavior
         */
        public void setSwipeUpProxy(Function<GestureState, AnimatedFloat> proxy) {
            TouchInteractionService tis = mTis.get();
            if (tis == null)
                return;
            tis.mSwipeUpProxyProvider = proxy != null ? proxy : (i -> null);
        }

        /**
         * Sets the task id where gestures should be blocked
         */
        public void setGestureBlockedTaskId(int taskId) {
            TouchInteractionService tis = mTis.get();
            if (tis == null)
                return;
            tis.mDeviceState.setGestureBlockingTaskId(taskId);
        }

        /** Sets a listener to be run on Overview Target updates. */
        public void setOverviewTargetChangeListener(@Nullable Runnable listener) {
            mOnOverviewTargetChangeListener = listener;
        }

        protected void onOverviewTargetChange() {
            if (mOnOverviewTargetChangeListener != null) {
                mOnOverviewTargetChangeListener.run();
                mOnOverviewTargetChangeListener = null;
            }
        }
    }

    private static boolean sConnected = false;
    private static boolean sIsInitialized = false;
    private RotationTouchHelper mRotationTouchHelper;

    public static boolean isConnected() {
        return sConnected;
    }

    public static boolean isInitialized() {
        return sIsInitialized;
    }

    private final AbsSwipeUpHandler.Factory mLauncherSwipeHandlerFactory = this::createLauncherSwipeHandler;
    private final AbsSwipeUpHandler.Factory mFallbackSwipeHandlerFactory = this::createFallbackSwipeHandler;

    private ActivityManagerWrapper mAM;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private InputConsumerController mInputConsumer;
    private RecentsAnimationDeviceState mDeviceState;
    private TaskAnimationManager mTaskAnimationManager;

    private @NonNull InputConsumer mUncheckedConsumer = InputConsumer.NO_OP;
    private @NonNull InputConsumer mConsumer = InputConsumer.NO_OP;
    private Choreographer mMainChoreographer;
    private @Nullable ResetGestureInputConsumer mResetGestureInputConsumer;
    private GestureState mGestureState = DEFAULT_STATE;

    private InputMonitorCompat mInputMonitorCompat;
    private InputEventReceiver mInputEventReceiver;

    private TaskbarManager mTaskbarManager;
    private Function<GestureState, AnimatedFloat> mSwipeUpProxyProvider = i -> null;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!LawnchairApp.isRecentsEnabled())
            return;
        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mMainChoreographer = Choreographer.getInstance();
        mAM = ActivityManagerWrapper.getInstance();
        mDeviceState = new RecentsAnimationDeviceState(this, true);
        mTaskbarManager = new TaskbarManager(this);
        mRotationTouchHelper = mDeviceState.getRotationTouchHelper();
        BootAwarePreloader.start(this);

        // Call runOnUserUnlocked() before any other callbacks to ensure everything is
        // initialized.
        LockedUserState.get(this).runOnUserUnlocked(this::onUserUnlocked);
        LockedUserState.get(this).runOnUserUnlocked(mTaskbarManager::onUserUnlocked);
        mDeviceState.addNavigationModeChangedCallback(this::onNavigationModeChanged);
        sConnected = true;
    }

    private void disposeEventHandlers(String reason) {
        Log.d(TAG, "disposeEventHandlers: Reason: " + reason);
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitorCompat != null) {
            mInputMonitorCompat.dispose();
            mInputMonitorCompat = null;
        }
    }

    private void initInputMonitor(String reason) {
        disposeEventHandlers("Initializing input monitor due to: " + reason);

        if (mDeviceState.isButtonNavMode() && !ENABLE_TRACKPAD_GESTURE.get()) {
            return;
        }

        mInputMonitorCompat = new InputMonitorCompat("swipe-up", mDeviceState.getDisplayId());
        mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                mMainChoreographer, this::onInputEvent);

        mRotationTouchHelper.updateGestureTouchRegions();
    }

    /**
     * Called when the navigation mode changes, guaranteed to be after the device
     * state has updated.
     */
    private void onNavigationModeChanged() {
        initInputMonitor("onNavigationModeChanged()");
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }

    @UiThread
    public void onUserUnlocked() {
        mTaskAnimationManager = new TaskAnimationManager(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this, mDeviceState);
        mOverviewCommandHelper = new OverviewCommandHelper(this,
                mOverviewComponentObserver, mTaskAnimationManager);
        mResetGestureInputConsumer = new ResetGestureInputConsumer(
                mTaskAnimationManager, mTaskbarManager::getCurrentActivityContext);
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();
        mInputConsumer.registerInputConsumer();
        onSystemUiFlagsChanged(mDeviceState.getSystemUiStateFlags());
        onAssistantVisibilityChanged();

        // Initialize the task tracker
        TopTaskTracker.INSTANCE.get(this);

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();

        mOverviewComponentObserver.setOverviewChangeListener(this::onOverviewTargetChange);
        onOverviewTargetChange(mOverviewComponentObserver.isHomeAndOverviewSame());
    }

    public OverviewCommandHelper getOverviewCommandHelper() {
        return mOverviewCommandHelper;
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (!LockedUserState.get(this).isUserUnlocked() || mDeviceState.isButtonNavMode()) {
            // Skip if not yet unlocked (can't read user shared prefs) or if the current
            // navigation
            // mode doesn't have gestures
            return;
        }

        // Reset home bounce seen on quick step enabled for first time
        SharedPreferences sharedPrefs = LauncherPrefs.getPrefs(this);
        if (!sharedPrefs.getBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)) {
            sharedPrefs.edit()
                    .putBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)
                    .putBoolean(OnboardingPrefs.HOME_BOUNCE_SEEN, false)
                    .apply();
        }
    }

    private void onOverviewTargetChange(boolean isHomeAndOverviewSame) {
        AccessibilityManager am = getSystemService(AccessibilityManager.class);

        if (isHomeAndOverviewSame) {
            am.registerSystemAction(createAllAppsAction(), GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
        } else {
            am.unregisterSystemAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
        }

        StatefulActivity newOverviewActivity = mOverviewComponentObserver.getActivityInterface()
                .getCreatedActivity();
        if (newOverviewActivity != null) {
            mTaskbarManager.setActivity(newOverviewActivity);
        }
        mTISBinder.onOverviewTargetChange();
    }

    private RemoteAction createAllAppsAction() {
        final Intent homeIntent = new Intent(mOverviewComponentObserver.getHomeIntent())
                .setAction(INTENT_ACTION_ALL_APPS_TOGGLE);
        final PendingIntent actionPendingIntent;

        if (FeatureFlags.ENABLE_ALL_APPS_SEARCH_IN_TASKBAR.get()) {
            actionPendingIntent = new PendingIntent(new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder allowlistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) {
                    MAIN_EXECUTOR.execute(() -> mTaskbarManager.toggleAllApps(homeIntent));
                }
            });
        } else {
            actionPendingIntent = PendingIntent.getActivity(
                    this,
                    GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS,
                    homeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        return new RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_apps),
                getString(R.string.all_apps_label),
                getString(R.string.all_apps_label),
                actionPendingIntent);
    }

    @UiThread
    private void onSystemUiFlagsChanged(int lastSysUIFlags) {
        if (LockedUserState.get(this).isUserUnlocked()) {
            int systemUiStateFlags = mDeviceState.getSystemUiStateFlags();
            SystemUiProxy.INSTANCE.get(this).setLastSystemUiStateFlags(systemUiStateFlags);
            mOverviewComponentObserver.onSystemUiStateChanged();
            mTaskbarManager.onSystemUiFlagsChanged(systemUiStateFlags);

            int isShadeExpandedFlag = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
            boolean wasExpanded = (lastSysUIFlags & isShadeExpandedFlag) != 0;
            boolean isExpanded = (systemUiStateFlags & isShadeExpandedFlag) != 0;
            if (wasExpanded != isExpanded && isExpanded) {
                // End live tile when expanding the notification panel for the first time from
                // overview.
                mTaskAnimationManager.endLiveTile();
            }
        }
    }

    @UiThread
    private void onAssistantVisibilityChanged() {
        if (LockedUserState.get(this).isUserUnlocked()) {
            mOverviewComponentObserver.getActivityInterface().onAssistantVisibilityChanged(
                    mDeviceState.getAssistantVisibility());
        }
    }

    @Override
    public void onDestroy() {
        if (!LawnchairApp.isRecentsEnabled()) {
            super.onDestroy();
            return;
        }
        Log.d(TAG, "Touch service destroyed: user=" + getUserId());
        sIsInitialized = false;
        if (LockedUserState.get(this).isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.onDestroy();
        }
        disposeEventHandlers("TouchInteractionService onDestroy()");
        mDeviceState.destroy();
        SystemUiProxy.INSTANCE.get(this).clearProxy();

        getSystemService(AccessibilityManager.class)
                .unregisterSystemAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);

        mTaskbarManager.destroy();
        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected: user=" + getUserId());
        return mTISBinder;
    }

    private void onInputEvent(InputEvent ev) {
        if (!(ev instanceof MotionEvent)) {
            Log.e(TAG, "Unknown event " + ev);
            return;
        }
        MotionEvent event = (MotionEvent) ev;

        TestLogging.recordMotionEvent(
                TestProtocol.SEQUENCE_TIS, "TouchInteractionService.onInputEvent", event);

        if (!LockedUserState.get(this).isUserUnlocked() || (mDeviceState.isButtonNavMode()
                && !isTrackpadMotionEvent(event))) {
            return;
        }

        SafeCloseable traceToken = TraceHelper.INSTANCE.allowIpcs("TIS.onInputEvent");

        final int action = event.getActionMasked();
        // Note this will create a new consumer every mouse click, as after ACTION_UP
        // from the click
        // an ACTION_HOVER_ENTER will fire as well.
        boolean isHoverActionWithoutConsumer = event.isHoverEvent()
                && (mUncheckedConsumer.getType() & TYPE_CURSOR_HOVER) == 0;
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            mRotationTouchHelper.setOrientationTransformIfNeeded(event);

            if ((!mDeviceState.isOneHandedModeActive()
                    && mRotationTouchHelper.isInSwipeUpTouchRegion(event))
                    || isHoverActionWithoutConsumer) {
                // Clone the previous gesture state since onConsumerAboutToBeSwitched might
                // trigger
                // onConsumerInactive and wipe the previous gesture state
                GestureState prevGestureState = new GestureState(mGestureState);
                GestureState newGestureState = createGestureState(mGestureState,
                        getTrackpadGestureType(event));
                newGestureState.setSwipeUpStartTimeMs(SystemClock.uptimeMillis());
                mConsumer.onConsumerAboutToBeSwitched();
                mGestureState = newGestureState;
                mConsumer = newConsumer(prevGestureState, mGestureState, event);
                mUncheckedConsumer = mConsumer;
            } else if (LockedUserState.get(this).isUserUnlocked()
                    && (mDeviceState.isFullyGesturalNavMode() || isTrackpadMultiFingerSwipe(event))
                    && mDeviceState.canTriggerAssistantAction(event)) {
                mGestureState = createGestureState(mGestureState,
                        getTrackpadGestureType(event));
                // Do not change mConsumer as if there is an ongoing QuickSwitch gesture, we
                // should not interrupt it. QuickSwitch assumes that interruption can only
                // happen if the next gesture is also quick switch.
                mUncheckedConsumer = tryCreateAssistantInputConsumer(mGestureState, event);
            } else if (mDeviceState.canTriggerOneHandedAction(event)) {
                // Consume gesture event for triggering one handed feature.
                mUncheckedConsumer = new OneHandedModeInputConsumer(this, mDeviceState,
                        InputConsumer.NO_OP, mInputMonitorCompat);
            } else {
                mUncheckedConsumer = InputConsumer.NO_OP;
            }
        } else {
            // Other events
            if (mUncheckedConsumer != InputConsumer.NO_OP) {
                // Only transform the event if we are handling it in a proper consumer
                mRotationTouchHelper.setOrientationTransformIfNeeded(event);
            }
        }

        if (mUncheckedConsumer != InputConsumer.NO_OP) {
            switch (event.getActionMasked()) {
                case ACTION_DOWN:
                    // fall through
                case ACTION_UP:
                    ActiveGestureLog.INSTANCE.addLog(
                            /* event= */ "onMotionEvent(" + (int) event.getRawX() + ", "
                                    + (int) event.getRawY() + "): "
                                    + MotionEvent.actionToString(event.getActionMasked()) + ", "
                                    + MotionEvent.classificationToString(event.getClassification()),
                            /* gestureEvent= */ event.getActionMasked() == ACTION_DOWN
                                    ? MOTION_DOWN
                                    : MOTION_UP);
                    break;
                case ACTION_MOVE:
                    ActiveGestureLog.INSTANCE.addLog("onMotionEvent: "
                            + MotionEvent.actionToString(event.getActionMasked()) + ","
                            + MotionEvent.classificationToString(event.getClassification())
                            + ", pointerCount: " + event.getPointerCount(), MOTION_MOVE);
                    break;
                default: {
                    ActiveGestureLog.INSTANCE.addLog("onMotionEvent: "
                            + MotionEvent.actionToString(event.getActionMasked()) + ","
                            + MotionEvent.classificationToString(event.getClassification()));
                }
            }
        }

        boolean cancelGesture = mGestureState.getActivityInterface() != null
                && mGestureState.getActivityInterface().shouldCancelCurrentGesture();
        boolean cleanUpConsumer = (action == ACTION_UP || action == ACTION_CANCEL || cancelGesture)
                && mConsumer != null
                && !mConsumer.getActiveConsumerInHierarchy().isConsumerDetachedFromGesture();
        if (cancelGesture) {
            event.setAction(ACTION_CANCEL);
        }

        if (mGestureState.isTrackpadGesture() && (action == ACTION_POINTER_DOWN
                || action == ACTION_POINTER_UP)) {
            // Skip ACTION_POINTER_DOWN and ACTION_POINTER_UP events from trackpad.
        } else if (isCursorHoverEvent(event)) {
            mUncheckedConsumer.onHoverEvent(event);
        } else {
            mUncheckedConsumer.onMotionEvent(event);
        }

        if (cleanUpConsumer) {
            reset();
        }
        traceToken.close();
    }

    // Talkback generates hover events on touch, which we do not want to consume.
    private boolean isCursorHoverEvent(MotionEvent event) {
        return event.isHoverEvent() && event.getSource() == InputDevice.SOURCE_MOUSE;
    }

    private InputConsumer tryCreateAssistantInputConsumer(
            GestureState gestureState, MotionEvent motionEvent) {
        return tryCreateAssistantInputConsumer(
                InputConsumer.NO_OP, gestureState, motionEvent, CompoundString.NO_OP);
    }

    private InputConsumer tryCreateAssistantInputConsumer(
            InputConsumer base,
            GestureState gestureState,
            MotionEvent motionEvent,
            CompoundString reasonString) {
        if (mDeviceState.isGestureBlockedTask(gestureState.getRunningTask())) {
            reasonString.append(SUBSTRING_PREFIX)
                    .append("is gesture-blocked task, using base input consumer");
            return base;
        } else {
            reasonString.append(SUBSTRING_PREFIX).append("using AssistantInputConsumer");
            return new AssistantInputConsumer(
                    this, gestureState, base, mInputMonitorCompat, mDeviceState, motionEvent);
        }
    }

    public GestureState createGestureState(GestureState previousGestureState,
            GestureState.TrackpadGestureType trackpadGestureType) {
        final GestureState gestureState;
        TopTaskTracker.CachedTaskInfo taskInfo;
        if (mTaskAnimationManager.isRecentsAnimationRunning()) {
            gestureState = new GestureState(mOverviewComponentObserver,
                    ActiveGestureLog.INSTANCE.getLogId());
            taskInfo = previousGestureState.getRunningTask();
            gestureState.updateRunningTask(taskInfo);
            gestureState.updateLastStartedTaskIds(previousGestureState.getLastStartedTaskIds());
            gestureState.updatePreviouslyAppearedTaskIds(
                    previousGestureState.getPreviouslyAppearedTaskIds());
        } else {
            gestureState = new GestureState(mOverviewComponentObserver,
                    ActiveGestureLog.INSTANCE.incrementLogId());
            taskInfo = TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false);
            gestureState.updateRunningTask(taskInfo);
        }
        gestureState.setTrackpadGestureType(trackpadGestureType);

        // Log initial state for the gesture.
        ActiveGestureLog.INSTANCE.addLog(new CompoundString("Current running task package name=")
                .append(taskInfo == null ? "no running task" : taskInfo.getPackageName()));
        ActiveGestureLog.INSTANCE.addLog(new CompoundString("Current SystemUi state flags=")
                .append(mDeviceState.getSystemUiStateString()));
        return gestureState;
    }

    private InputConsumer newConsumer(
            GestureState previousGestureState, GestureState newGestureState, MotionEvent event) {
        AnimatedFloat progressProxy = mSwipeUpProxyProvider.apply(mGestureState);
        if (progressProxy != null) {
            InputConsumer consumer = new ProgressDelegateInputConsumer(
                    this, mTaskAnimationManager, mGestureState, mInputMonitorCompat, progressProxy);

            logInputConsumerSelectionReason(consumer, newCompoundString(
                    "mSwipeUpProxyProvider has been set, using ProgressDelegateInputConsumer"));

            return consumer;
        }

        boolean canStartSystemGesture = mGestureState.isTrackpadGesture() ? mDeviceState.canStartTrackpadGesture()
                : mDeviceState.canStartSystemGesture();

        if (!LockedUserState.get(this).isUserUnlocked()) {
            CompoundString reasonString = newCompoundString("device locked");
            InputConsumer consumer;
            if (canStartSystemGesture) {
                // This handles apps launched in direct boot mode (e.g. dialer) as well as apps
                // launched while device is locked even after exiting direct boot mode (e.g.
                // camera).
                consumer = createDeviceLockedInputConsumer(
                        newGestureState, reasonString.append(SUBSTRING_PREFIX)
                                .append("can start system gesture"));
            } else {
                consumer = getDefaultInputConsumer(
                        reasonString.append(SUBSTRING_PREFIX)
                                .append("cannot start system gesture"));
            }
            logInputConsumerSelectionReason(consumer, reasonString);
            return consumer;
        }

        CompoundString reasonString;
        InputConsumer base;
        // When there is an existing recents animation running, bypass systemState check
        // as this is
        // a followup gesture and the first gesture started in a valid system state.
        if (canStartSystemGesture || previousGestureState.isRecentsAnimationRunning()) {
            reasonString = newCompoundString(canStartSystemGesture
                    ? "can start system gesture"
                    : "recents animation was running")
                    .append(", trying to use base consumer");
            base = newBaseConsumer(previousGestureState, newGestureState, event, reasonString);
        } else {
            reasonString = newCompoundString(
                    "cannot start system gesture and recents animation was not running")
                    .append(", trying to use default input consumer");
            base = getDefaultInputConsumer(reasonString);
        }
        if (mDeviceState.isGesturalNavMode() || newGestureState.isTrackpadGesture()) {
            handleOrientationSetup(base);
        }
        if (mDeviceState.isFullyGesturalNavMode() || newGestureState.isTrackpadGesture()) {
            String reasonPrefix = "device is in gesture navigation mode or 3-button mode with a"
                    + " trackpad gesture";
            if (mDeviceState.canTriggerAssistantAction(event)) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("gesture can trigger the assistant")
                        .append(", trying to use assistant input consumer");
                base = tryCreateAssistantInputConsumer(base, newGestureState, event, reasonString);
            }

            // If Taskbar is present, we listen for swipe or cursor hover events to unstash
            // it.
            TaskbarActivityContext tac = mTaskbarManager.getCurrentActivityContext();
            if (tac != null && !(base instanceof AssistantInputConsumer)) {
                // Present always on large screen or on small screen w/ flag
                DeviceProfile dp = tac.getDeviceProfile();
                boolean useTaskbarConsumer = dp.isTaskbarPresent && !TaskbarManager.isPhoneMode(dp)
                        && !tac.isInStashedLauncherState();
                if (canStartSystemGesture && useTaskbarConsumer) {
                    reasonString.append(NEWLINE_PREFIX)
                            .append(reasonPrefix)
                            .append(SUBSTRING_PREFIX)
                            .append("TaskbarActivityContext != null, "
                                    + "using TaskbarUnstashInputConsumer");
                    base = new TaskbarUnstashInputConsumer(this, base, mInputMonitorCompat, tac,
                            mOverviewCommandHelper);
                }
            } else if (canStartSystemGesture && FeatureFlags.ENABLE_LONG_PRESS_NAV_HANDLE.get()
                    && !previousGestureState.isRecentsAnimationRunning()) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("Long press nav handle enabled, "
                                + "using NavHandleLongPressInputConsumer");
                base = new NavHandleLongPressInputConsumer(this, base, mInputMonitorCompat);
            }

            if (mDeviceState.isBubblesExpanded()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("bubbles expanded, trying to use default input consumer");
                // Bubbles can handle home gesture itself.
                base = getDefaultInputConsumer(reasonString);
            }

            if (mDeviceState.isSystemUiDialogShowing()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("system dialog is showing, using SysUiOverlayInputConsumer");
                base = new SysUiOverlayInputConsumer(
                        getBaseContext(), mDeviceState, mInputMonitorCompat);
            }

            if (ENABLE_TRACKPAD_GESTURE.get() && mGestureState.isTrackpadGesture()
                    && canStartSystemGesture && !previousGestureState.isRecentsAnimationRunning()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("Trackpad 3-finger gesture, using TrackpadStatusBarInputConsumer");
                base = new TrackpadStatusBarInputConsumer(getBaseContext(), base,
                        mInputMonitorCompat);
            }

            if (mDeviceState.isScreenPinningActive()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("screen pinning is active, using ScreenPinnedInputConsumer");
                // Note: we only allow accessibility to wrap this, and it replaces the previous
                // base input consumer (which should be NO_OP anyway since topTaskLocked ==
                // true).
                base = new ScreenPinnedInputConsumer(this, newGestureState);
            }

            if (mDeviceState.canTriggerOneHandedAction(event)) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("gesture can trigger one handed mode")
                        .append(", using OneHandedModeInputConsumer");
                base = new OneHandedModeInputConsumer(
                        this, mDeviceState, base, mInputMonitorCompat);
            }

            if (mDeviceState.isAccessibilityMenuAvailable()) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("accessibility menu is available")
                        .append(", using AccessibilityInputConsumer");
                base = new AccessibilityInputConsumer(
                        this, mDeviceState, mGestureState, base, mInputMonitorCompat);
            }
        } else {
            String reasonPrefix = "device is not in gesture navigation mode";
            if (mDeviceState.isScreenPinningActive()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("screen pinning is active, trying to use default input consumer");
                base = getDefaultInputConsumer(reasonString);
            }

            if (mDeviceState.canTriggerOneHandedAction(event)) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("gesture can trigger one handed mode")
                        .append(", using OneHandedModeInputConsumer");
                base = new OneHandedModeInputConsumer(
                        this, mDeviceState, base, mInputMonitorCompat);
            }
        }
        logInputConsumerSelectionReason(base, reasonString);
        return base;
    }

    private CompoundString newCompoundString(String substring) {
        return new CompoundString(NEWLINE_PREFIX).append(substring);
    }

    private void logInputConsumerSelectionReason(
            InputConsumer consumer, CompoundString reasonString) {
        ActiveGestureLog.INSTANCE.addLog(new CompoundString("setInputConsumer: ")
                .append(consumer.getName())
                .append(". reason(s):")
                .append(reasonString));
        if ((consumer.getType() & InputConsumer.TYPE_OTHER_ACTIVITY) != 0) {
            ActiveGestureLog.INSTANCE.trackEvent(FLAG_USING_OTHER_ACTIVITY_INPUT_CONSUMER);
        }
    }

    private void handleOrientationSetup(InputConsumer baseInputConsumer) {
        baseInputConsumer.notifyOrientationSetup();
    }

    private InputConsumer newBaseConsumer(
            GestureState previousGestureState,
            GestureState gestureState,
            MotionEvent event,
            CompoundString reasonString) {
        if (mDeviceState.isKeyguardShowingOccluded()) {
            // This handles apps showing over the lockscreen (e.g. camera)
            return createDeviceLockedInputConsumer(
                    gestureState,
                    reasonString.append(SUBSTRING_PREFIX)
                            .append("keyguard is showing occluded")
                            .append(", trying to use device locked input consumer"));
        }

        reasonString.append(SUBSTRING_PREFIX).append("keyguard is not showing occluded");

        // Use overview input consumer for sharesheets on top of home.
        boolean forceOverviewInputConsumer = gestureState.getActivityInterface().isStarted()
                && gestureState.getRunningTask() != null
                && gestureState.getRunningTask().isRootChooseActivity();

        // In the case where we are in an excluded, translucent overlay, ignore it and
        // treat the
        // running activity as the task behind the overlay.
        TopTaskTracker.CachedTaskInfo otherVisibleTask = gestureState.getRunningTask() == null
                ? null
                : gestureState.getRunningTask().otherVisibleTaskThisIsExcludedOver();
        if (otherVisibleTask != null) {
            ActiveGestureLog.INSTANCE.addLog(new CompoundString("Changing active task to ")
                    .append(otherVisibleTask.getPackageName())
                    .append(" because the previous task running on top of this one (")
                    .append(gestureState.getRunningTask().getPackageName())
                    .append(") was excluded from recents"));
            gestureState.updateRunningTask(otherVisibleTask);
        }

        boolean previousGestureAnimatedToLauncher = previousGestureState.isRunningAnimationToLauncher();
        // with shell-transitions, home is resumed during recents animation, so
        // explicitly check against recents animation too.
        boolean launcherResumedThroughShellTransition = gestureState.getActivityInterface().isResumed()
                && !previousGestureState.isRecentsAnimationRunning();
        if (gestureState.getActivityInterface().isInLiveTileMode()) {
            return createOverviewInputConsumer(
                    previousGestureState,
                    gestureState,
                    event,
                    forceOverviewInputConsumer,
                    reasonString.append(SUBSTRING_PREFIX)
                            .append("is in live tile mode, trying to use overview input consumer"));
        } else if (gestureState.getRunningTask() == null) {
            return getDefaultInputConsumer(reasonString.append(SUBSTRING_PREFIX)
                    .append("running task == null"));
        } else if (previousGestureAnimatedToLauncher
                || launcherResumedThroughShellTransition
                || forceOverviewInputConsumer) {
            return createOverviewInputConsumer(
                    previousGestureState,
                    gestureState,
                    event,
                    forceOverviewInputConsumer,
                    reasonString.append(SUBSTRING_PREFIX)
                            .append(previousGestureAnimatedToLauncher
                                    ? "previous gesture animated to launcher"
                                    : (launcherResumedThroughShellTransition
                                            ? "launcher resumed through a shell transition"
                                            : "forceOverviewInputConsumer == true"))
                            .append(", trying to use overview input consumer"));
        } else if (mDeviceState.isGestureBlockedTask(gestureState.getRunningTask())) {
            return getDefaultInputConsumer(reasonString.append(SUBSTRING_PREFIX)
                    .append("is gesture-blocked task, trying to use default input consumer"));
        } else {
            reasonString.append(SUBSTRING_PREFIX)
                    .append("using OtherActivityInputConsumer");
            return createOtherActivityInputConsumer(gestureState, event);
        }
    }

    public AbsSwipeUpHandler.Factory getSwipeUpHandlerFactory() {
        return !mOverviewComponentObserver.isHomeAndOverviewSame()
                ? mFallbackSwipeHandlerFactory
                : mLauncherSwipeHandlerFactory;
    }

    private InputConsumer createOtherActivityInputConsumer(GestureState gestureState,
            MotionEvent event) {

        final AbsSwipeUpHandler.Factory factory = getSwipeUpHandlerFactory();
        final boolean shouldDefer = !mOverviewComponentObserver.isHomeAndOverviewSame()
                || gestureState.getActivityInterface().deferStartingActivity(mDeviceState, event);
        final boolean disableHorizontalSwipe = mDeviceState.isInExclusionRegion(event);
        return new OtherActivityInputConsumer(this, mDeviceState, mTaskAnimationManager,
                gestureState, shouldDefer, this::onConsumerInactive,
                mInputMonitorCompat, mInputEventReceiver, disableHorizontalSwipe, factory);
    }

    private InputConsumer createDeviceLockedInputConsumer(
            GestureState gestureState, CompoundString reasonString) {
        if ((mDeviceState.isFullyGesturalNavMode() || gestureState.isTrackpadGesture())
                && gestureState.getRunningTask() != null) {
            reasonString.append(SUBSTRING_PREFIX)
                    .append("device is in gesture nav mode or 3-button mode with a trackpad gesture"
                            + "and running task != null")
                    .append(", using DeviceLockedInputConsumer");
            return new DeviceLockedInputConsumer(
                    this, mDeviceState, mTaskAnimationManager, gestureState, mInputMonitorCompat);
        } else {
            return getDefaultInputConsumer(reasonString
                    .append(SUBSTRING_PREFIX)
                    .append((mDeviceState.isFullyGesturalNavMode()
                            || gestureState.isTrackpadGesture())
                                    ? "running task == null"
                                    : "device is not in gesture nav mode and it's not a trackpad gesture")
                    .append(", trying to use default input consumer"));
        }
    }

    public InputConsumer createOverviewInputConsumer(
            GestureState previousGestureState,
            GestureState gestureState,
            MotionEvent event,
            boolean forceOverviewInputConsumer,
            CompoundString reasonString) {
        StatefulActivity activity = gestureState.getActivityInterface().getCreatedActivity();
        if (activity == null) {
            return getDefaultInputConsumer(
                    reasonString.append(SUBSTRING_PREFIX)
                            .append("activity == null, trying to use default input consumer"));
        }

        boolean hasWindowFocus = activity.getRootView().hasWindowFocus();
        boolean isPreviousGestureAnimatingToLauncher = previousGestureState.isRunningAnimationToLauncher();
        boolean isInLiveTileMode = gestureState.getActivityInterface().isInLiveTileMode();
        reasonString.append(SUBSTRING_PREFIX)
                .append(hasWindowFocus
                        ? "activity has window focus"
                        : (isPreviousGestureAnimatingToLauncher
                                ? "previous gesture is still animating to launcher"
                                : isInLiveTileMode
                                        ? "device is in live mode"
                                        : "all overview focus conditions failed"));
        if (hasWindowFocus
                || isPreviousGestureAnimatingToLauncher
                || isInLiveTileMode) {
            reasonString.append(SUBSTRING_PREFIX)
                    .append("overview should have focus, using OverviewInputConsumer");
            return new OverviewInputConsumer(gestureState, activity, mInputMonitorCompat,
                    false /* startingInActivityBounds */);
        } else {
            reasonString.append(SUBSTRING_PREFIX).append(
                    "overview shouldn't have focus, using OverviewWithoutFocusInputConsumer");
            final boolean disableHorizontalSwipe = mDeviceState.isInExclusionRegion(event);
            return new OverviewWithoutFocusInputConsumer(activity, mDeviceState, gestureState,
                    mInputMonitorCompat, disableHorizontalSwipe);
        }
    }

    /**
     * To be called by the consumer when it's no longer active. This can be called
     * by any consumer
     * in the hierarchy at any point during the gesture (ie. if a delegate consumer
     * starts
     * intercepting touches, the base consumer can try to call this).
     */
    private void onConsumerInactive(InputConsumer caller) {
        if (mConsumer != null && mConsumer.getActiveConsumerInHierarchy() == caller) {
            reset();
        }
    }

    private void reset() {
        mConsumer = mUncheckedConsumer = getDefaultInputConsumer();
        mGestureState = DEFAULT_STATE;
        // By default, use batching of the input events, but check receiver before using
        // in the rare
        // case that the monitor was disposed before the swipe settled
        if (mInputEventReceiver != null) {
            mInputEventReceiver.setBatchingEnabled(true);
        }
    }

    private @NonNull InputConsumer getDefaultInputConsumer() {
        return getDefaultInputConsumer(CompoundString.NO_OP);
    }

    /**
     * Returns the {@link ResetGestureInputConsumer} if user is unlocked, else
     * NO_OP.
     */
    private @NonNull InputConsumer getDefaultInputConsumer(@NonNull CompoundString reasonString) {
        if (mResetGestureInputConsumer != null) {
            reasonString.append(SUBSTRING_PREFIX).append(
                    "mResetGestureInputConsumer initialized, using ResetGestureInputConsumer");
            return mResetGestureInputConsumer;
        } else {
            reasonString.append(SUBSTRING_PREFIX).append(
                    "mResetGestureInputConsumer not initialized, using no-op input consumer");
            // mResetGestureInputConsumer isn't initialized until onUserUnlocked(), so reset
            // to
            // NO_OP until then (we never want these to be null).
            return InputConsumer.NO_OP;
        }
    }

    private void preloadOverview(boolean fromInit) {
        Trace.beginSection("preloadOverview(fromInit=" + fromInit + ")");
        preloadOverview(fromInit, false);
        Trace.endSection();
    }

    private void preloadOverview(boolean fromInit, boolean forSUWAllSet) {
        if (!LockedUserState.get(this).isUserUnlocked()) {
            return;
        }

        if (mDeviceState.isButtonNavMode() && !mOverviewComponentObserver.isHomeAndOverviewSame()) {
            // Prevent the overview from being started before the real home on first boot.
            return;
        }

        if ((RestoreDbTask.isPending(this) && !forSUWAllSet)
                || !mDeviceState.isUserSetupComplete()) {
            // Preloading while a restore is pending may cause launcher to start the restore
            // too early.
            return;
        }

        final BaseActivityInterface activityInterface = mOverviewComponentObserver.getActivityInterface();
        final Intent overviewIntent = new Intent(
                mOverviewComponentObserver.getOverviewIntentIgnoreSysUiState());
        if (activityInterface.getCreatedActivity() != null && fromInit) {
            // The activity has been created before the initialization of overview service.
            // It is
            // usually happens when booting or launcher is the top activity, so we should
            // already
            // have the latest state.
            return;
        }

        // TODO(b/258022658): Remove temporary logging.
        Log.i(TAG, "preloadOverview: forSUWAllSet=" + forSUWAllSet
                + ", isHomeAndOverviewSame=" + mOverviewComponentObserver.isHomeAndOverviewSame());

        mTaskAnimationManager.preloadRecentsAnimation(overviewIntent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (!LockedUserState.get(this).isUserUnlocked()) {
            return;
        }
        final BaseActivityInterface activityInterface = mOverviewComponentObserver.getActivityInterface();
        final BaseDraggingActivity activity = activityInterface.getCreatedActivity();
        if (activity == null || activity.isStarted()) {
            // We only care about the existing background activity.
            return;
        }
        Configuration oldConfig = activity.getResources().getConfiguration();
        boolean isFoldUnfold = isTablet(oldConfig) != isTablet(newConfig);
        if (!isFoldUnfold && mOverviewComponentObserver.canHandleConfigChanges(
                activity.getComponentName(),
                activity.getResources().getConfiguration().diff(newConfig))) {
            // Since navBar gestural height are different between portrait and landscape,
            // can handle orientation changes and refresh navigation gestural region through
            // onOneHandedModeChanged()
            int newGesturalHeight = ResourceUtils.getNavbarSize(
                    ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                    getApplicationContext().getResources());
            mDeviceState.onOneHandedModeChanged(newGesturalHeight);
            return;
        }

        preloadOverview(false /* fromInit */);
    }

    private static boolean isTablet(Configuration config) {
        return config.smallestScreenWidthDp >= MIN_TABLET_WIDTH;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] rawArgs) {
        // Dump everything
        FlagsFactory.dump(pw);
        if (LockedUserState.get(this).isUserUnlocked()) {
            PluginManagerWrapper.INSTANCE.get(getBaseContext()).dump(pw);
        }
        mDeviceState.dump(pw);
        if (mOverviewComponentObserver != null) {
            mOverviewComponentObserver.dump(pw);
        }
        if (mOverviewCommandHelper != null) {
            mOverviewCommandHelper.dump(pw);
        }
        if (mGestureState != null) {
            mGestureState.dump(pw);
        }
        pw.println("Input state:");
        pw.println("  mInputMonitorCompat=" + mInputMonitorCompat);
        pw.println("  mInputEventReceiver=" + mInputEventReceiver);
        DisplayController.INSTANCE.get(this).dump(pw);
        pw.println("TouchState:");
        BaseDraggingActivity createdOverviewActivity = mOverviewComponentObserver == null ? null
                : mOverviewComponentObserver.getActivityInterface().getCreatedActivity();
        boolean resumed = mOverviewComponentObserver != null
                && mOverviewComponentObserver.getActivityInterface().isResumed();
        pw.println("  createdOverviewActivity=" + createdOverviewActivity);
        pw.println("  resumed=" + resumed);
        pw.println("  mConsumer=" + mConsumer.getName());
        ActiveGestureLog.INSTANCE.dump("", pw);
        RecentsModel.INSTANCE.get(this).dump("", pw);
        if (createdOverviewActivity != null) {
            createdOverviewActivity.getDeviceProfile().dump(this, "", pw);
        }
        mTaskbarManager.dumpLogs("", pw);
        pw.println("AssistStateManager:");
        AssistStateManager.INSTANCE.get(this).dump("  ", pw);
    }

    private AbsSwipeUpHandler createLauncherSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        return new LauncherSwipeHandlerV2(this, mDeviceState, mTaskAnimationManager,
                gestureState, touchTimeMs, mTaskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer);
    }

    private AbsSwipeUpHandler createFallbackSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        return new FallbackSwipeHandler(this, mDeviceState, mTaskAnimationManager,
                gestureState, touchTimeMs, mTaskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer);
    }
}
