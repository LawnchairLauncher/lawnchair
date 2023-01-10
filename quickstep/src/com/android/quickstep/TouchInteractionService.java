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
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.config.FeatureFlags.ASSISTANT_GIVES_LAUNCHER_FOCUS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.GestureState.DEFAULT_STATE;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.FLAG_USING_OTHER_ACTIVITY_INPUT_CONSUMER;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_DOWN;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_UP;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_TRACING_ENABLED;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_BACK_ANIMATION;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_DESKTOP_MODE;
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Region;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.app.viewcapture.ViewCapture;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.tracing.LauncherTraceProto;
import com.android.launcher3.tracing.TouchInteractionServiceProto;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer;
import com.android.quickstep.inputconsumers.AssistantInputConsumer;
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer;
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer;
import com.android.quickstep.inputconsumers.ProgressDelegateInputConsumer;
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer;
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer;
import com.android.quickstep.inputconsumers.SysUiOverlayInputConsumer;
import com.android.quickstep.inputconsumers.TaskbarStashInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureLog.CompoundString;
import com.android.quickstep.util.ProtoTracer;
import com.android.quickstep.util.ProxyScreenStatusProvider;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController;
import com.android.systemui.shared.tracing.ProtoTraceable;
import com.android.wm.shell.back.IBackAnimation;
import com.android.wm.shell.desktopmode.IDesktopMode;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.pip.IPip;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.startingsurface.IStartingWindow;
import com.android.wm.shell.transition.IShellTransitions;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.Function;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.R)
public class TouchInteractionService extends Service
        implements ProtoTraceable<LauncherTraceProto.Builder> {

    private static final String SUBSTRING_PREFIX = "; ";
    private static final String NEWLINE_PREFIX = "\n\t\t\t-> ";

    private static final String TAG = "TouchInteractionService";

    private static final String HAS_ENABLED_QUICKSTEP_ONCE = "launcher.has_enabled_quickstep_once";

    private final TISBinder mTISBinder = new TISBinder();

    /**
     * Local IOverviewProxy implementation with some methods for local components
     */
    public class TISBinder extends IOverviewProxy.Stub {

        @Nullable private Runnable mOnOverviewTargetChangeListener = null;

        @BinderThread
        public void onInitialize(Bundle bundle) {
            ISystemUiProxy proxy = ISystemUiProxy.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            IPip pip = IPip.Stub.asInterface(bundle.getBinder(KEY_EXTRA_SHELL_PIP));
            ISplitScreen splitscreen = ISplitScreen.Stub.asInterface(bundle.getBinder(
                    KEY_EXTRA_SHELL_SPLIT_SCREEN));
            IOneHanded onehanded = IOneHanded.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_ONE_HANDED));
            IShellTransitions shellTransitions = IShellTransitions.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_SHELL_TRANSITIONS));
            IStartingWindow startingWindow = IStartingWindow.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_STARTING_WINDOW));
            ISysuiUnlockAnimationController launcherUnlockAnimationController =
                    ISysuiUnlockAnimationController.Stub.asInterface(
                            bundle.getBinder(KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER));
            IRecentTasks recentTasks = IRecentTasks.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_RECENT_TASKS));
            IBackAnimation backAnimation = IBackAnimation.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_BACK_ANIMATION));
            IDesktopMode desktopMode = IDesktopMode.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SHELL_DESKTOP_MODE));
            MAIN_EXECUTOR.execute(() -> {
                SystemUiProxy.INSTANCE.get(TouchInteractionService.this).setProxy(proxy, pip,
                        splitscreen, onehanded, shellTransitions, startingWindow,
                        recentTasks, launcherUnlockAnimationController, backAnimation, desktopMode);
                TouchInteractionService.this.initInputMonitor("TISBinder#onInitialize()");
                preloadOverview(true /* fromInit */);
            });
            sIsInitialized = true;
        }

        @BinderThread
        public void onOverviewToggle() {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
            // If currently screen pinning, do not enter overview
            if (mDeviceState.isScreenPinningActive()) {
                return;
            }
            TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
            mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_TOGGLE);
        }

        @BinderThread
        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            if (triggeredFromAltTab) {
                TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_SHOW_NEXT_FOCUS);
            } else {
                mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_SHOW);
            }
        }

        @BinderThread
        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab hides the overview and ends at the target app
                mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_HIDE);
            }
        }

        @BinderThread
        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {
            MAIN_EXECUTOR.execute(() -> {
                mDeviceState.setAssistantAvailable(available);
                TouchInteractionService.this.onAssistantVisibilityChanged();
                executeForTaskbarManager(() -> mTaskbarManager
                        .onLongPressHomeEnabled(longPressHomeEnabled));
            });
        }

        @BinderThread
        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            MAIN_EXECUTOR.execute(() -> {
                mDeviceState.setAssistantVisibility(visibility);
                TouchInteractionService.this.onAssistantVisibilityChanged();
            });
        }

        @Override
        public void onNavigationBarSurface(SurfaceControl surface) {
            // TODO: implement
        }

        @BinderThread
        public void onSystemUiStateChanged(int stateFlags) {
            MAIN_EXECUTOR.execute(() -> {
                int lastFlags = mDeviceState.getSystemUiStateFlags();
                mDeviceState.setSystemUiFlags(stateFlags);
                TouchInteractionService.this.onSystemUiFlagsChanged(lastFlags);
            });
        }

        @BinderThread
        public void onActiveNavBarRegionChanges(Region region) {
            MAIN_EXECUTOR.execute(() -> mDeviceState.setDeferredGestureRegion(region));
        }

        @BinderThread
        @Override
        public void onScreenTurnedOn() {
            MAIN_EXECUTOR.execute(ProxyScreenStatusProvider.INSTANCE::onScreenTurnedOn);
        }

        @BinderThread
        @Override
        public void onScreenTurningOn() {
            MAIN_EXECUTOR.execute(ProxyScreenStatusProvider.INSTANCE::onScreenTurningOn);
        }

        @BinderThread
        @Override
        public void onScreenTurningOff() {
            MAIN_EXECUTOR.execute(ProxyScreenStatusProvider.INSTANCE::onScreenTurningOff);
        }

        @BinderThread
        @Override
        public void enterStageSplitFromRunningApp(boolean leftOrTop) {
            StatefulActivity activity =
                    mOverviewComponentObserver.getActivityInterface().getCreatedActivity();
            if (activity != null) {
                activity.enterStageSplitFromRunningApp(leftOrTop);
            }
        }

        /**
         * Preloads the Overview activity.
         *
         * This method should only be used when the All Set page of the SUW is reached to safely
         * preload the Launcher for the SUW first reveal.
         */
        public void preloadOverviewForSUWAllSet() {
            preloadOverview(false, true);
        }

        @Override
        public void onRotationProposal(int rotation, boolean isValid) {
            executeForTaskbarManager(() -> mTaskbarManager.onRotationProposal(rotation, isValid));
        }

        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {
            executeForTaskbarManager(() -> mTaskbarManager
                    .disableNavBarElements(displayId, state1, state2, animate));
        }

        @Override
        public void onSystemBarAttributesChanged(int displayId, int behavior) {
            executeForTaskbarManager(() -> mTaskbarManager
                    .onSystemBarAttributesChanged(displayId, behavior));
        }

        @Override
        public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
            executeForTaskbarManager(() -> mTaskbarManager
                    .onNavButtonsDarkIntensityChanged(darkIntensity));
        }

        private void executeForTaskbarManager(final Runnable r) {
            MAIN_EXECUTOR.execute(() -> {
                if (mTaskbarManager == null) {
                    return;
                }
                r.run();
            });
        }

        public TaskbarManager getTaskbarManager() {
            return mTaskbarManager;
        }

        public OverviewCommandHelper getOverviewCommandHelper() {
            return mOverviewCommandHelper;
        }

        /**
         * Sets a proxy to bypass swipe up behavior
         */
        public void setSwipeUpProxy(Function<GestureState, AnimatedFloat> proxy) {
            mSwipeUpProxyProvider = proxy != null ? proxy : (i -> null);
        }

        /**
         * Sets the task id where gestures should be blocked
         */
        public void setGestureBlockedTaskId(int taskId) {
            mDeviceState.setGestureBlockingTaskId(taskId);
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

    private final AbsSwipeUpHandler.Factory mLauncherSwipeHandlerFactory =
            this::createLauncherSwipeHandler;
    private final AbsSwipeUpHandler.Factory mFallbackSwipeHandlerFactory =
            this::createFallbackSwipeHandler;

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
        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mMainChoreographer = Choreographer.getInstance();
        mAM = ActivityManagerWrapper.getInstance();
        mDeviceState = new RecentsAnimationDeviceState(this, true);
        mTaskbarManager = new TaskbarManager(this);
        mRotationTouchHelper = mDeviceState.getRotationTouchHelper();

        // Call runOnUserUnlocked() before any other callbacks to ensure everything is initialized.
        mDeviceState.runOnUserUnlocked(this::onUserUnlocked);
        mDeviceState.runOnUserUnlocked(mTaskbarManager::onUserUnlocked);
        mDeviceState.addNavigationModeChangedCallback(this::onNavigationModeChanged);

        ProtoTracer.INSTANCE.get(this).add(this);
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

        if (mDeviceState.isButtonNavMode()) {
            return;
        }

        mInputMonitorCompat = new InputMonitorCompat("swipe-up", mDeviceState.getDisplayId());
        mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                mMainChoreographer, this::onInputEvent);

        mRotationTouchHelper.updateGestureTouchRegions();
    }

    /**
     * Called when the navigation mode changes, guaranteed to be after the device state has updated.
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
        if (!mDeviceState.isUserUnlocked() || mDeviceState.isButtonNavMode()) {
            // Skip if not yet unlocked (can't read user shared prefs) or if the current navigation
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
            Intent intent = new Intent(mOverviewComponentObserver.getHomeIntent())
                    .setAction(Intent.ACTION_ALL_APPS);
            RemoteAction allAppsAction = new RemoteAction(
                    Icon.createWithResource(this, R.drawable.ic_apps),
                    getString(R.string.all_apps_label),
                    getString(R.string.all_apps_label),
                    PendingIntent.getActivity(this, GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            am.registerSystemAction(allAppsAction, GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
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

    @UiThread
    private void onSystemUiFlagsChanged(int lastSysUIFlags) {
        if (mDeviceState.isUserUnlocked()) {
            int systemUiStateFlags = mDeviceState.getSystemUiStateFlags();
            SystemUiProxy.INSTANCE.get(this).setLastSystemUiStateFlags(systemUiStateFlags);
            mOverviewComponentObserver.onSystemUiStateChanged();
            mTaskbarManager.onSystemUiFlagsChanged(systemUiStateFlags);

            boolean wasFreeformActive =
                    (lastSysUIFlags & SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE) != 0;
            boolean isFreeformActive =
                    (systemUiStateFlags & SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE) != 0;
            if (wasFreeformActive != isFreeformActive) {
                DesktopVisibilityController controller = mOverviewComponentObserver
                        .getActivityInterface().getDesktopVisibilityController();
                if (controller != null) {
                    controller.setFreeformTasksVisible(isFreeformActive);
                }
            }

            int isShadeExpandedFlag =
                    SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
            boolean wasExpanded = (lastSysUIFlags & isShadeExpandedFlag) != 0;
            boolean isExpanded = (systemUiStateFlags & isShadeExpandedFlag) != 0;
            if (wasExpanded != isExpanded && isExpanded) {
                // End live tile when expanding the notification panel for the first time from
                // overview.
                mTaskAnimationManager.endLiveTile();
            }

            if ((lastSysUIFlags & SYSUI_STATE_TRACING_ENABLED) !=
                    (systemUiStateFlags & SYSUI_STATE_TRACING_ENABLED)) {
                // Update the tracing state
                if ((systemUiStateFlags & SYSUI_STATE_TRACING_ENABLED) != 0) {
                    Log.d(TAG, "Starting tracing.");
                    ProtoTracer.INSTANCE.get(this).start();
                } else {
                    Log.d(TAG, "Stopping tracing. Dumping to file="
                            + ProtoTracer.INSTANCE.get(this).getTraceFile());
                    ProtoTracer.INSTANCE.get(this).stop();
                }
            }
        }
    }

    @UiThread
    private void onAssistantVisibilityChanged() {
        if (mDeviceState.isUserUnlocked()) {
            mOverviewComponentObserver.getActivityInterface().onAssistantVisibilityChanged(
                    mDeviceState.getAssistantVisibility());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Touch service destroyed: user=" + getUserId());
        sIsInitialized = false;
        if (mDeviceState.isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.onDestroy();
        }
        disposeEventHandlers("TouchInteractionService onDestroy()");
        mDeviceState.destroy();
        SystemUiProxy.INSTANCE.get(this).clearProxy();
        ProtoTracer.INSTANCE.get(this).stop();
        ProtoTracer.INSTANCE.get(this).remove(this);

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

        if (!mDeviceState.isUserUnlocked()) {
            return;
        }

        Object traceToken = TraceHelper.INSTANCE.beginFlagsOverride(
                TraceHelper.FLAG_ALLOW_BINDER_TRACKING);

        final int action = event.getAction();
        if (action == ACTION_DOWN) {
            mRotationTouchHelper.setOrientationTransformIfNeeded(event);

            if (!mDeviceState.isOneHandedModeActive()
                    && mRotationTouchHelper.isInSwipeUpTouchRegion(event)) {
                // Clone the previous gesture state since onConsumerAboutToBeSwitched might trigger
                // onConsumerInactive and wipe the previous gesture state
                GestureState prevGestureState = new GestureState(mGestureState);
                GestureState newGestureState = createGestureState(mGestureState);
                newGestureState.setSwipeUpStartTimeMs(SystemClock.uptimeMillis());
                mConsumer.onConsumerAboutToBeSwitched();
                mGestureState = newGestureState;
                mConsumer = newConsumer(prevGestureState, mGestureState, event);
                mUncheckedConsumer = mConsumer;
            } else if (mDeviceState.isUserUnlocked() && mDeviceState.isFullyGesturalNavMode()
                    && mDeviceState.canTriggerAssistantAction(event)) {
                mGestureState = createGestureState(mGestureState);
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
                case ACTION_UP:
                    ActiveGestureLog.INSTANCE.addLog(
                            /* event= */ "onMotionEvent(" + (int) event.getRawX() + ", "
                                    + (int) event.getRawY() + "): "
                                    + MotionEvent.actionToString(event.getActionMasked()),
                            /* gestureEvent= */ event.getActionMasked() == ACTION_DOWN
                                    ? MOTION_DOWN
                                    : MOTION_UP);
                    break;
                default:
                    ActiveGestureLog.INSTANCE.addLog("onMotionEvent: "
                            + MotionEvent.actionToString(event.getActionMasked()));
                    break;
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
        mUncheckedConsumer.onMotionEvent(event);

        if (cleanUpConsumer) {
            reset();
        }
        TraceHelper.INSTANCE.endFlagsOverride(traceToken);
        ProtoTracer.INSTANCE.get(this).scheduleFrameUpdate();
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

    public GestureState createGestureState(GestureState previousGestureState) {
        final GestureState gestureState;
        TopTaskTracker.CachedTaskInfo taskInfo;
        if (mTaskAnimationManager.isRecentsAnimationRunning()) {
            gestureState = new GestureState(mOverviewComponentObserver,
                    ActiveGestureLog.INSTANCE.getLogId());
            taskInfo = previousGestureState.getRunningTask();
            gestureState.updateRunningTask(taskInfo);
            gestureState.updateLastStartedTaskId(previousGestureState.getLastStartedTaskId());
            gestureState.updatePreviouslyAppearedTaskIds(
                    previousGestureState.getPreviouslyAppearedTaskIds());
        } else {
            gestureState = new GestureState(mOverviewComponentObserver,
                    ActiveGestureLog.INSTANCE.incrementLogId());
            taskInfo = TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false);
            gestureState.updateRunningTask(taskInfo);
        }
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

        boolean canStartSystemGesture = mDeviceState.canStartSystemGesture();

        if (!mDeviceState.isUserUnlocked()) {
            CompoundString reasonString = newCompoundString("device locked");
            InputConsumer consumer;
            if (canStartSystemGesture) {
                // This handles apps launched in direct boot mode (e.g. dialer) as well as apps
                // launched while device is locked even after exiting direct boot mode (e.g. camera).
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
        // When there is an existing recents animation running, bypass systemState check as this is
        // a followup gesture and the first gesture started in a valid system state.
        if (canStartSystemGesture || previousGestureState.isRecentsAnimationRunning()) {
            reasonString = newCompoundString(canStartSystemGesture
                    ? "can start system gesture" : "recents animation was running")
                    .append(", trying to use base consumer");
            base = newBaseConsumer(previousGestureState, newGestureState, event, reasonString);
        } else {
            reasonString = newCompoundString(
                    "cannot start system gesture and recents animation was not running")
                    .append(", trying to use default input consumer");
            base = getDefaultInputConsumer(reasonString);
        }
        if (mDeviceState.isGesturalNavMode()) {
            handleOrientationSetup(base);
        }
        if (mDeviceState.isFullyGesturalNavMode()) {
            String reasonPrefix = "device is in gesture navigation mode";
            if (mDeviceState.canTriggerAssistantAction(event)) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("gesture can trigger the assistant")
                        .append(", trying to use assistant input consumer");
                base = tryCreateAssistantInputConsumer(base, newGestureState, event, reasonString);
            }

            // If Taskbar is present, we listen for long press to unstash it.
            TaskbarActivityContext tac = mTaskbarManager.getCurrentActivityContext();
            if (tac != null) {
                reasonString.append(NEWLINE_PREFIX)
                        .append(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("TaskbarActivityContext != null, using TaskbarStashInputConsumer");
                base = new TaskbarStashInputConsumer(this, base, mInputMonitorCompat, tac);
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



            if (mDeviceState.isScreenPinningActive()) {
                reasonString = newCompoundString(reasonPrefix)
                        .append(SUBSTRING_PREFIX)
                        .append("screen pinning is active, using ScreenPinnedInputConsumer");
                // Note: we only allow accessibility to wrap this, and it replaces the previous
                // base input consumer (which should be NO_OP anyway since topTaskLocked == true).
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
                        this, mDeviceState, base, mInputMonitorCompat);
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
        if (!FeatureFlags.ENABLE_INPUT_CONSUMER_REASON_LOGGING.get()) {
            ActiveGestureLog.INSTANCE.addLog("setInputConsumer: " + consumer.getName());
            return;
        }
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
        if (gestureState.getRunningTask() != null
                && gestureState.getRunningTask().isExcludedAssistant()) {
            // In the case where we are in the excluded assistant state, ignore it and treat the
            // running activity as the task behind the assistant
            gestureState.updateRunningTask(TopTaskTracker.INSTANCE.get(this)
                    .getCachedTopTask(true /* filterOnlyVisibleRecents */));
            forceOverviewInputConsumer = gestureState.getRunningTask().isHomeTask();
        }

        boolean previousGestureAnimatedToLauncher =
                previousGestureState.isRunningAnimationToLauncher();
        // with shell-transitions, home is resumed during recents animation, so
        // explicitly check against recents animation too.
        boolean launcherResumedThroughShellTransition =
                gestureState.getActivityInterface().isResumed()
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
                ? mFallbackSwipeHandlerFactory : mLauncherSwipeHandlerFactory;
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
        if (mDeviceState.isFullyGesturalNavMode() && gestureState.getRunningTask() != null) {
            reasonString.append(SUBSTRING_PREFIX)
                    .append("device is in gesture nav mode and running task != null")
                    .append(", using DeviceLockedInputConsumer");
            return new DeviceLockedInputConsumer(
                    this, mDeviceState, mTaskAnimationManager, gestureState, mInputMonitorCompat);
        } else {
            return getDefaultInputConsumer(reasonString
                    .append(SUBSTRING_PREFIX)
                    .append(mDeviceState.isFullyGesturalNavMode()
                        ? "running task == null" : "device is not in gesture nav mode")
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
        boolean isPreviousGestureAnimatingToLauncher =
                previousGestureState.isRunningAnimationToLauncher();
        boolean forcingOverviewInputConsumer =
                ASSISTANT_GIVES_LAUNCHER_FOCUS.get() && forceOverviewInputConsumer;
        boolean isInLiveTileMode = gestureState.getActivityInterface().isInLiveTileMode();
        reasonString.append(SUBSTRING_PREFIX)
                .append(hasWindowFocus
                        ? "activity has window focus"
                        : (isPreviousGestureAnimatingToLauncher
                                ? "previous gesture is still animating to launcher"
                                : (forcingOverviewInputConsumer
                                        ? "assistant gives launcher focus and forcing focus"
                                        : (isInLiveTileMode
                                                ? "device is in live mode"
                                                : "all overview focus conditions failed"))));
        if (hasWindowFocus
                || isPreviousGestureAnimatingToLauncher
                || forcingOverviewInputConsumer
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
     * To be called by the consumer when it's no longer active. This can be called by any consumer
     * in the hierarchy at any point during the gesture (ie. if a delegate consumer starts
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
        // By default, use batching of the input events, but check receiver before using in the rare
        // case that the monitor was disposed before the swipe settled
        if (mInputEventReceiver != null) {
            mInputEventReceiver.setBatchingEnabled(true);
        }
    }

    private @NonNull InputConsumer getDefaultInputConsumer() {
        return getDefaultInputConsumer(CompoundString.NO_OP);
    }

    /**
     * Returns the {@link ResetGestureInputConsumer} if user is unlocked, else NO_OP.
     */
    private @NonNull InputConsumer getDefaultInputConsumer(@NonNull CompoundString reasonString) {
        if (mResetGestureInputConsumer != null) {
            reasonString.append(SUBSTRING_PREFIX).append(
                    "mResetGestureInputConsumer initialized, using ResetGestureInputConsumer");
            return mResetGestureInputConsumer;
        } else {
            reasonString.append(SUBSTRING_PREFIX).append(
                    "mResetGestureInputConsumer not initialized, using no-op input consumer");
            // mResetGestureInputConsumer isn't initialized until onUserUnlocked(), so reset to
            // NO_OP until then (we never want these to be null).
            return InputConsumer.NO_OP;
        }
    }

    private void preloadOverview(boolean fromInit) {
        preloadOverview(fromInit, false);
    }

    private void preloadOverview(boolean fromInit, boolean forSUWAllSet) {
        if (!mDeviceState.isUserUnlocked()) {
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

        final BaseActivityInterface activityInterface =
                mOverviewComponentObserver.getActivityInterface();
        final Intent overviewIntent = new Intent(
                mOverviewComponentObserver.getOverviewIntentIgnoreSysUiState());
        if (activityInterface.getCreatedActivity() != null && fromInit) {
            // The activity has been created before the initialization of overview service. It is
            // usually happens when booting or launcher is the top activity, so we should already
            // have the latest state.
            return;
        }

        mTaskAnimationManager.preloadRecentsAnimation(overviewIntent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (!mDeviceState.isUserUnlocked()) {
            return;
        }
        final BaseActivityInterface activityInterface =
                mOverviewComponentObserver.getActivityInterface();
        final BaseDraggingActivity activity = activityInterface.getCreatedActivity();
        if (activity == null || activity.isStarted()) {
            // We only care about the existing background activity.
            return;
        }
        if (mOverviewComponentObserver.canHandleConfigChanges(activity.getComponentName(),
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

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] rawArgs) {
        if (rawArgs.length > 0 && Utilities.IS_DEBUG_DEVICE) {
            LinkedList<String> args = new LinkedList(Arrays.asList(rawArgs));
            switch (args.pollFirst()) {
                case "cmd":
                    if (args.peekFirst() == null) {
                        printAvailableCommands(pw);
                    } else {
                        onCommand(pw, args);
                    }
                    break;
            }
        } else {
            // Dump everything
            FeatureFlags.dump(pw);
            if (mDeviceState.isUserUnlocked()) {
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
            pw.println("ProtoTrace:");
            pw.println("  file=" + ProtoTracer.INSTANCE.get(this).getTraceFile());
            if (createdOverviewActivity != null) {
                createdOverviewActivity.getDeviceProfile().dump(this, "", pw);
            }
            mTaskbarManager.dumpLogs("", pw);

            if (FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
                ViewCapture.getInstance().dump(pw, fd, this);
            }
        }
    }

    private void printAvailableCommands(PrintWriter pw) {
        pw.println("Available commands:");
        pw.println("  clear-touch-log: Clears the touch interaction log");
        pw.println("  print-gesture-log: only prints the ActiveGestureLog dump");
    }

    private void onCommand(PrintWriter pw, LinkedList<String> args) {
        String cmd = args.pollFirst();
        if (cmd == null) {
            pw.println("Command missing");
            printAvailableCommands(pw);
            return;
        }
        switch (cmd) {
            case "clear-touch-log":
                ActiveGestureLog.INSTANCE.clear();
                break;
            case "print-gesture-log":
                ActiveGestureLog.INSTANCE.dump("", pw);
                break;
            default:
                pw.println("Command does not exist: " + cmd);
                printAvailableCommands(pw);
        }
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

    @Override
    public void writeToProto(LauncherTraceProto.Builder proto) {
        TouchInteractionServiceProto.Builder serviceProto =
            TouchInteractionServiceProto.newBuilder();
        serviceProto.setServiceConnected(true);

        if (mOverviewComponentObserver != null) {
            mOverviewComponentObserver.writeToProto(serviceProto);
        }
        mConsumer.writeToProto(serviceProto);

        proto.setTouchInteractionService(serviceProto);
    }
}
