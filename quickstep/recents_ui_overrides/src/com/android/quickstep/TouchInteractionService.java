/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;
import static com.android.launcher3.config.FeatureFlags.APPLY_CONFIG_AT_RUNTIME;
import static com.android.launcher3.config.FeatureFlags.ENABLE_HINTS_IN_OVERVIEW;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.config.FeatureFlags.FAKE_LANDSCAPE_UI;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_INPUT_MONITOR;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.uioverrides.DejankBinderTracker;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer;
import com.android.quickstep.inputconsumers.AssistantInputConsumer;
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer;
import com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer;
import com.android.quickstep.inputconsumers.QuickCaptureInputConsumer;
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer;
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.TaskInfoCompat;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Wrapper around a list for processing arguments.
 */
class ArgList extends LinkedList<String> {
    public ArgList(List<String> l) {
        super(l);
    }

    public String peekArg() {
        return peekFirst();
    }

    public String nextArg() {
        return pollFirst().toLowerCase();
    }
}

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class TouchInteractionService extends Service implements
        NavigationModeChangeListener {

    private static final String TAG = "TouchInteractionService";

    private static final String KEY_BACK_NOTIFICATION_COUNT = "backNotificationCount";
    private static final String NOTIFY_ACTION_BACK = "com.android.quickstep.action.BACK_GESTURE";
    private static final String HAS_ENABLED_QUICKSTEP_ONCE = "launcher.has_enabled_quickstep_once";
    private static final int MAX_BACK_NOTIFICATION_COUNT = 3;
    private int mBackGestureNotificationCounter = -1;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        public void onInitialize(Bundle bundle) {
            ISystemUiProxy proxy = ISystemUiProxy.Stub.asInterface(
                    bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            MAIN_EXECUTOR.execute(() -> SystemUiProxy.INSTANCE.get(TouchInteractionService.this)
                    .setProxy(proxy));
            MAIN_EXECUTOR.execute(TouchInteractionService.this::initInputMonitor);
            MAIN_EXECUTOR.execute(() -> preloadOverview(true /* fromInit */));
            sIsInitialized = true;
        }

        @Override
        public void onOverviewToggle() {
            mOverviewCommandHelper.onOverviewToggle();
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            mOverviewCommandHelper.onOverviewShown(triggeredFromAltTab);
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab hides the overview and ends at the target app
                mOverviewCommandHelper.onOverviewHidden();
            }
        }

        @Override
        public void onTip(int actionType, int viewType) {
            mOverviewCommandHelper.onTip(actionType, viewType);
        }

        @Override
        public void onAssistantAvailable(boolean available) {
            MAIN_EXECUTOR.execute(() -> mDeviceState.setAssistantAvailable(available));
            MAIN_EXECUTOR.execute(TouchInteractionService.this::onAssistantVisibilityChanged);
        }

        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            MAIN_EXECUTOR.execute(() -> mDeviceState.setAssistantVisibility(visibility));
            MAIN_EXECUTOR.execute(TouchInteractionService.this::onAssistantVisibilityChanged);
        }

        public void onBackAction(boolean completed, int downX, int downY, boolean isButton,
                boolean gestureSwipeLeft) {
            if (mOverviewComponentObserver == null) {
                return;
            }

            final ActivityControlHelper activityControl =
                    mOverviewComponentObserver.getActivityControlHelper();
            UserEventDispatcher.newInstance(getBaseContext()).logActionBack(completed, downX, downY,
                    isButton, gestureSwipeLeft, activityControl.getContainerType());

            if (completed && !isButton && shouldNotifyBackGesture()) {
                UI_HELPER_EXECUTOR.execute(TouchInteractionService.this::tryNotifyBackGesture);
            }
        }

        public void onSystemUiStateChanged(int stateFlags) {
            MAIN_EXECUTOR.execute(() -> mDeviceState.setSystemUiFlags(stateFlags));
            MAIN_EXECUTOR.execute(TouchInteractionService.this::onSystemUiFlagsChanged);
        }

        public void onActiveNavBarRegionChanges(Region region) {
            MAIN_EXECUTOR.execute(() -> mDeviceState.setDeferredGestureRegion(region));
        }

        /** Deprecated methods **/
        public void onQuickStep(MotionEvent motionEvent) { }

        public void onQuickScrubEnd() { }

        public void onQuickScrubProgress(float progress) { }

        public void onQuickScrubStart() { }

        public void onPreMotionEvent(int downHitTarget) { }

        public void onMotionEvent(MotionEvent ev) {
            ev.recycle();
        }

        public void onBind(ISystemUiProxy iSystemUiProxy) { }
    };

    private static boolean sConnected = false;
    private static boolean sIsInitialized = false;
    private static final SwipeSharedState sSwipeSharedState = new SwipeSharedState();
    private int mLogId;

    public static boolean isConnected() {
        return sConnected;
    }

    public static boolean isInitialized() {
        return sIsInitialized;
    }

    public static SwipeSharedState getSwipeSharedState() {
        return sSwipeSharedState;
    }

    private final InputConsumer mResetGestureInputConsumer =
            new ResetGestureInputConsumer(sSwipeSharedState);

    private final BaseSwipeUpHandler.Factory mWindowTreansformFactory =
            this::createWindowTransformSwipeHandler;
    private final BaseSwipeUpHandler.Factory mFallbackNoButtonFactory =
            this::createFallbackNoButtonSwipeHandler;

    private ActivityManagerWrapper mAM;
    private RecentsModel mRecentsModel;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private InputConsumerController mInputConsumer;
    private RecentsAnimationDeviceState mDeviceState;

    private InputConsumer mUncheckedConsumer = InputConsumer.NO_OP;
    private InputConsumer mConsumer = InputConsumer.NO_OP;
    private Choreographer mMainChoreographer;

    private InputMonitorCompat mInputMonitorCompat;
    private InputEventReceiver mInputEventReceiver;
    private Mode mMode = Mode.THREE_BUTTONS;

    @Override
    public void onCreate() {
        super.onCreate();
        mDeviceState = new RecentsAnimationDeviceState(this);
        mDeviceState.runOnUserUnlocked(this::onUserUnlocked);

        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mMainChoreographer = Choreographer.getInstance();
        mAM = ActivityManagerWrapper.getInstance();

        onNavigationModeChanged(SysUINavigationMode.INSTANCE.get(this).addModeChangeListener(this));
        sConnected = true;
    }

    private void disposeEventHandlers() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "disposeEventHandlers");
            }
        }
        if (mInputMonitorCompat != null) {
            mInputMonitorCompat.dispose();
            mInputMonitorCompat = null;
        }
    }

    private void initInputMonitor() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "initInputMonitor 1");
        }
        disposeEventHandlers();
        if (!mMode.hasGestures || !SystemUiProxy.INSTANCE.get(this).isActive()) {
            return;
        }
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "initInputMonitor 2");
        }

        Bundle bundle = SystemUiProxy.INSTANCE.get(this).monitorGestureInput("swipe-up",
                mDeviceState.getDisplayId());
        mInputMonitorCompat = InputMonitorCompat.fromBundle(bundle, KEY_EXTRA_INPUT_MONITOR);
        mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                mMainChoreographer, this::onInputEvent);
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "initInputMonitor 3");
        }

        mDeviceState.updateGestureTouchRegions();
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "onNavigationModeChanged " + newMode);
        }
        mMode = newMode;
        initInputMonitor();
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }

    public void onUserUnlocked() {
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this, mDeviceState);
        mOverviewCommandHelper = new OverviewCommandHelper(this, mOverviewComponentObserver);
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();

        sSwipeSharedState.setOverviewComponentObserver(mOverviewComponentObserver);
        mInputConsumer.registerInputConsumer();
        onSystemUiFlagsChanged();
        onAssistantVisibilityChanged();

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        mBackGestureNotificationCounter = Math.max(0, Utilities.getDevicePrefs(this)
                .getInt(KEY_BACK_NOTIFICATION_COUNT, MAX_BACK_NOTIFICATION_COUNT));
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (!mDeviceState.isUserUnlocked() || !mMode.hasGestures) {
            // Skip if not yet unlocked (can't read user shared prefs) or if the current navigation
            // mode doesn't have gestures
            return;
        }

        // Reset home bounce seen on quick step enabled for first time
        SharedPreferences sharedPrefs = Utilities.getPrefs(this);
        if (!sharedPrefs.getBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)) {
            sharedPrefs.edit()
                    .putBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)
                    .putBoolean(DiscoveryBounce.HOME_BOUNCE_SEEN, false)
                    .apply();
        }
    }

    @UiThread
    private void onSystemUiFlagsChanged() {
        if (mDeviceState.isUserUnlocked()) {
            SystemUiProxy.INSTANCE.get(this).setLastSystemUiStateFlags(
                    mDeviceState.getSystemUiStateFlags());
            mOverviewComponentObserver.onSystemUiStateChanged();
        }
    }

    @UiThread
    private void onAssistantVisibilityChanged() {
        if (mDeviceState.isUserUnlocked()) {
            mOverviewComponentObserver.getActivityControlHelper().onAssistantVisibilityChanged(
                    mDeviceState.getAssistantVisibility());
        }
    }

    @Override
    public void onDestroy() {
        sIsInitialized = false;
        if (mDeviceState.isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.onDestroy();
        }
        disposeEventHandlers();
        mDeviceState.destroy();
        SysUINavigationMode.INSTANCE.get(this).removeModeChangeListener(this);
        SystemUiProxy.INSTANCE.get(this).setProxy(null);

        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void onInputEvent(InputEvent ev) {
        DejankBinderTracker.allowBinderTrackingInTests();
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "onInputEvent " + ev);
        }
        if (!(ev instanceof MotionEvent)) {
            Log.e(TAG, "Unknown event " + ev);
            return;
        }

        MotionEvent event = (MotionEvent) ev;
        if (event.getAction() == ACTION_DOWN) {
            mLogId = ActiveGestureLog.INSTANCE.generateAndSetLogId();
            sSwipeSharedState.setLogTraceId(mLogId);

            if (mDeviceState.isInSwipeUpTouchRegion(event)) {
                boolean useSharedState = mConsumer.useSharedSwipeState();
                mConsumer.onConsumerAboutToBeSwitched();
                mConsumer = newConsumer(useSharedState, event);
                ActiveGestureLog.INSTANCE.addLog("setInputConsumer", mConsumer.getType());
                mUncheckedConsumer = mConsumer;
            } else if (mDeviceState.isUserUnlocked() && mMode == Mode.NO_BUTTON
                    && mDeviceState.canTriggerAssistantAction(event)) {
                // Do not change mConsumer as if there is an ongoing QuickSwitch gesture, we should
                // not interrupt it. QuickSwitch assumes that interruption can only happen if the
                // next gesture is also quick switch.
                mUncheckedConsumer =
                        new AssistantInputConsumer(this,
                                mOverviewComponentObserver.getActivityControlHelper(),
                                InputConsumer.NO_OP, mInputMonitorCompat);
            } else {
                mUncheckedConsumer = InputConsumer.NO_OP;
            }
        }

        ActiveGestureLog.INSTANCE.addLog("onMotionEvent", event.getActionMasked());
        mUncheckedConsumer.onMotionEvent(event);
        DejankBinderTracker.disallowBinderTrackingInTests();
    }

    private InputConsumer newConsumer(boolean useSharedState, MotionEvent event) {
        boolean canStartSystemGesture = mDeviceState.canStartSystemGesture();

        if (!mDeviceState.isUserUnlocked()) {
            if (canStartSystemGesture) {
                // This handles apps launched in direct boot mode (e.g. dialer) as well as apps
                // launched while device is locked even after exiting direct boot mode (e.g. camera).
                return createDeviceLockedInputConsumer(mAM.getRunningTask(ACTIVITY_TYPE_ASSISTANT));
            } else {
                return mResetGestureInputConsumer;
            }
        }

        // When using sharedState, bypass systemState check as this is a followup gesture and the
        // first gesture started in a valid system state.
        InputConsumer base = canStartSystemGesture || useSharedState
                ? newBaseConsumer(useSharedState, event) : mResetGestureInputConsumer;
        if (mMode == Mode.NO_BUTTON) {
            final ActivityControlHelper activityControl =
                    mOverviewComponentObserver.getActivityControlHelper();
            if (mDeviceState.canTriggerAssistantAction(event)) {
                base = new AssistantInputConsumer(this, activityControl, base, mInputMonitorCompat);
            }

            if (FeatureFlags.ENABLE_QUICK_CAPTURE_GESTURE.get()) {
                // Put the Compose gesture as higher priority than the Assistant or base gestures
                base = new QuickCaptureInputConsumer(this, base, mInputMonitorCompat,
                        activityControl);
            }

            if (mDeviceState.isScreenPinningActive()) {
                // Note: we only allow accessibility to wrap this, and it replaces the previous
                // base input consumer (which should be NO_OP anyway since topTaskLocked == true).
                base = new ScreenPinnedInputConsumer(this, activityControl);
            }

            if (mDeviceState.isAccessibilityMenuAvailable()) {
                base = new AccessibilityInputConsumer(this, mDeviceState, base,
                        mInputMonitorCompat);
            }
        } else {
            if (mDeviceState.isScreenPinningActive()) {
                base = mResetGestureInputConsumer;
            }
        }
        return base;
    }

    private InputConsumer newBaseConsumer(boolean useSharedState, MotionEvent event) {
        RunningTaskInfo runningTaskInfo = DejankBinderTracker.whitelistIpcs(
                () -> mAM.getRunningTask(0));
        if (!useSharedState) {
            sSwipeSharedState.clearAllState(false /* finishAnimation */);
        }
        if (mDeviceState.isKeyguardShowingOccluded()) {
            // This handles apps showing over the lockscreen (e.g. camera)
            return createDeviceLockedInputConsumer(runningTaskInfo);
        }

        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();

        boolean forceOverviewInputConsumer = false;
        if (isExcludedAssistant(runningTaskInfo)) {
            // In the case where we are in the excluded assistant state, ignore it and treat the
            // running activity as the task behind the assistant
            runningTaskInfo = DejankBinderTracker.whitelistIpcs(
                    () -> mAM.getRunningTask(ACTIVITY_TYPE_ASSISTANT));
            if (!ActivityManagerWrapper.isHomeTask(runningTaskInfo)) {
                final ComponentName homeComponent =
                    mOverviewComponentObserver.getHomeIntent().getComponent();
                forceOverviewInputConsumer =
                    runningTaskInfo.baseIntent.getComponent(). equals(homeComponent);
            }
        }

        if (runningTaskInfo == null && !sSwipeSharedState.goingToLauncher
                && !sSwipeSharedState.recentsAnimationFinishInterrupted) {
            return mResetGestureInputConsumer;
        } else if (sSwipeSharedState.recentsAnimationFinishInterrupted) {
            // If the finish animation was interrupted, then continue using the other activity input
            // consumer but with the next task as the running task
            RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
            info.id = sSwipeSharedState.nextRunningTaskId;
            return createOtherActivityInputConsumer(event, info);
        } else if (sSwipeSharedState.goingToLauncher || activityControl.isResumed()
                || forceOverviewInputConsumer) {
            return createOverviewInputConsumer(event);
        } else if (ENABLE_QUICKSTEP_LIVE_TILE.get() && activityControl.isInLiveTileMode()) {
            return createOverviewInputConsumer(event);
        } else if (mDeviceState.isGestureBlockedActivity(runningTaskInfo)) {
            return mResetGestureInputConsumer;
        } else {
            return createOtherActivityInputConsumer(event, runningTaskInfo);
        }
    }

    private boolean isExcludedAssistant(TaskInfo info) {
        return info != null
                && TaskInfoCompat.getActivityType(info) == ACTIVITY_TYPE_ASSISTANT
                && (info.baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
    }

    private InputConsumer createOtherActivityInputConsumer(MotionEvent event,
            RunningTaskInfo runningTaskInfo) {

        final boolean shouldDefer;
        final BaseSwipeUpHandler.Factory factory;
        ActivityControlHelper activityControlHelper =
                mOverviewComponentObserver.getActivityControlHelper();

        if (mMode == Mode.NO_BUTTON && !mOverviewComponentObserver.isHomeAndOverviewSame()) {
            shouldDefer = !sSwipeSharedState.recentsAnimationFinishInterrupted;
            factory = mFallbackNoButtonFactory;
        } else {
            shouldDefer = activityControlHelper.deferStartingActivity(mDeviceState, event);
            factory = mWindowTreansformFactory;
        }

        final boolean disableHorizontalSwipe = mDeviceState.isInExclusionRegion(event);
        return new OtherActivityInputConsumer(this, mDeviceState, runningTaskInfo, shouldDefer,
                this::onConsumerInactive, sSwipeSharedState, mInputMonitorCompat,
                disableHorizontalSwipe, activityControlHelper, factory, mLogId);
    }

    private InputConsumer createDeviceLockedInputConsumer(RunningTaskInfo taskInfo) {
        if (mMode == Mode.NO_BUTTON && taskInfo != null) {
            return new DeviceLockedInputConsumer(this, mDeviceState, sSwipeSharedState,
                    mInputMonitorCompat, taskInfo.taskId, mLogId);
        } else {
            return mResetGestureInputConsumer;
        }
    }

    public InputConsumer createOverviewInputConsumer(MotionEvent event) {
        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        BaseDraggingActivity activity = activityControl.getCreatedActivity();
        if (activity == null) {
            return mResetGestureInputConsumer;
        }

        if (activity.getRootView().hasWindowFocus() || sSwipeSharedState.goingToLauncher) {
            return new OverviewInputConsumer(activity, mInputMonitorCompat,
                    false /* startingInActivityBounds */, activityControl);
        } else {
            final boolean disableHorizontalSwipe = mDeviceState.isInExclusionRegion(event);
            return new OverviewWithoutFocusInputConsumer(activity, mInputMonitorCompat,
                    activityControl, disableHorizontalSwipe);
        }
    }

    /**
     * To be called by the consumer when it's no longer active.
     */
    private void onConsumerInactive(InputConsumer caller) {
        if (mConsumer == caller) {
            mConsumer = mResetGestureInputConsumer;
            mUncheckedConsumer = mConsumer;
        }
    }

    private void preloadOverview(boolean fromInit) {
        if (!mDeviceState.isUserUnlocked()) {
            return;
        }
        if (!mMode.hasGestures && !mOverviewComponentObserver.isHomeAndOverviewSame()) {
            // Prevent the overview from being started before the real home on first boot.
            return;
        }

        if (RestoreDbTask.isPending(this)) {
            // Preloading while a restore is pending may cause launcher to start the restore
            // too early.
            return;
        }

        final ActivityControlHelper<BaseDraggingActivity> activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        if (activityControl.getCreatedActivity() == null) {
            // Make sure that UI states will be initialized.
            activityControl.createActivityInitListener((activity, wasVisible) -> {
                AppLaunchTracker.INSTANCE.get(activity);
                return false;
            }).register();
        } else if (fromInit) {
            // The activity has been created before the initialization of overview service. It is
            // usually happens when booting or launcher is the top activity, so we should already
            // have the latest state.
            return;
        }

        // Pass null animation handler to indicate this start is preload.
        startRecentsActivityAsync(mOverviewComponentObserver.getOverviewIntentIgnoreSysUiState(),
                null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (!mDeviceState.isUserUnlocked()) {
            return;
        }
        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        final BaseDraggingActivity activity = activityControl.getCreatedActivity();
        if (activity == null || activity.isStarted()) {
            // We only care about the existing background activity.
            return;
        }
        if (mOverviewComponentObserver.canHandleConfigChanges(activity.getComponentName(),
                activity.getResources().getConfiguration().diff(newConfig))) {
            return;
        }

        preloadOverview(false /* fromInit */);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] rawArgs) {
        if (rawArgs.length > 0 && Utilities.IS_DEBUG_DEVICE) {
            ArgList args = new ArgList(Arrays.asList(rawArgs));
            switch (args.nextArg()) {
                case "cmd":
                    if (args.peekArg() == null) {
                        printAvailableCommands(pw);
                    } else {
                        onCommand(pw, args);
                    }
                    break;
            }
        } else {
            // Dump everything
            mDeviceState.dump(pw);
            pw.println("TouchState:");
            pw.println("  navMode=" + mMode);
            boolean resumed = mOverviewComponentObserver != null
                    && mOverviewComponentObserver.getActivityControlHelper().isResumed();
            pw.println("  resumed=" + resumed);
            pw.println("  useSharedState=" + mConsumer.useSharedSwipeState());
            if (mConsumer.useSharedSwipeState()) {
                sSwipeSharedState.dump("    ", pw);
            }
            pw.println("  mConsumer=" + mConsumer.getName());
            pw.println("FeatureFlags:");
            pw.println("  APPLY_CONFIG_AT_RUNTIME=" + APPLY_CONFIG_AT_RUNTIME.get());
            pw.println("  QUICKSTEP_SPRINGS=" + QUICKSTEP_SPRINGS.get());
            pw.println("  ADAPTIVE_ICON_WINDOW_ANIM=" + ADAPTIVE_ICON_WINDOW_ANIM.get());
            pw.println("  ENABLE_QUICKSTEP_LIVE_TILE=" + ENABLE_QUICKSTEP_LIVE_TILE.get());
            pw.println("  ENABLE_HINTS_IN_OVERVIEW=" + ENABLE_HINTS_IN_OVERVIEW.get());
            pw.println("  FAKE_LANDSCAPE_UI=" + FAKE_LANDSCAPE_UI.get());
            ActiveGestureLog.INSTANCE.dump("", pw);
        }
    }

    private void printAvailableCommands(PrintWriter pw) {
        pw.println("Available commands:");
        pw.println("  clear-touch-log: Clears the touch interaction log");
    }

    private void onCommand(PrintWriter pw, ArgList args) {
        switch (args.nextArg()) {
            case "clear-touch-log":
                ActiveGestureLog.INSTANCE.clear();
                break;
        }
    }

    private BaseSwipeUpHandler createWindowTransformSwipeHandler(RunningTaskInfo runningTask,
            long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask) {
        return  new WindowTransformSwipeHandler(mDeviceState, runningTask, this, touchTimeMs,
                mOverviewComponentObserver, continuingLastGesture, mInputConsumer, mRecentsModel);
    }

    private BaseSwipeUpHandler createFallbackNoButtonSwipeHandler(RunningTaskInfo runningTask,
            long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask) {
        return new FallbackNoButtonInputConsumer(this, mOverviewComponentObserver, runningTask,
                mRecentsModel, mInputConsumer, isLikelyToStartNewTask, continuingLastGesture);
    }

    protected boolean shouldNotifyBackGesture() {
        return mBackGestureNotificationCounter > 0 &&
                mDeviceState.getGestureBlockedActivityPackage() != null;
    }

    @WorkerThread
    protected void tryNotifyBackGesture() {
        if (shouldNotifyBackGesture()) {
            mBackGestureNotificationCounter--;
            Utilities.getDevicePrefs(this).edit()
                    .putInt(KEY_BACK_NOTIFICATION_COUNT, mBackGestureNotificationCounter).apply();
            sendBroadcast(new Intent(NOTIFY_ACTION_BACK).setPackage(
                    mDeviceState.getGestureBlockedActivityPackage()));
        }
    }

    public static void startRecentsActivityAsync(Intent intent, RecentsAnimationListener listener) {
        UI_HELPER_EXECUTOR.execute(() -> ActivityManagerWrapper.getInstance()
                .startRecentsActivity(intent, null, listener, null, null));
    }
}
