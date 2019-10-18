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
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.BinderThread;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.ResourceUtils;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.BaseFlags;
import com.android.launcher3.logging.EventLogArray;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.DefaultDisplay;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer;
import com.android.quickstep.inputconsumers.AssistantTouchConsumer;
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer;
import com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer;
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer;
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer;
import com.android.quickstep.util.AssistantUtilities;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.SystemGestureExclusionListenerCompat;

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
        NavigationModeChangeListener, DefaultDisplay.DisplayInfoChangeListener {

    /**
     * NOTE: This value should be kept same as
     * ActivityTaskManagerService#INTENT_EXTRA_LOG_TRACE_ID in platform
     */
    public static final String INTENT_EXTRA_LOG_TRACE_ID = "INTENT_EXTRA_LOG_TRACE_ID";


    public static final EventLogArray TOUCH_INTERACTION_LOG =
            new EventLogArray("touch_interaction_log", 40);

    private static final String TAG = "TouchInteractionService";

    private static final String KEY_BACK_NOTIFICATION_COUNT = "backNotificationCount";
    private static final String NOTIFY_ACTION_BACK = "com.android.quickstep.action.BACK_GESTURE";
    private static final int MAX_BACK_NOTIFICATION_COUNT = 3;
    private int mBackGestureNotificationCounter = -1;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        public void onActiveNavBarRegionChanges(Region region) {
            mActiveNavBarRegion = region;
        }

        public void onInitialize(Bundle bundle) {
            mISystemUiProxy = ISystemUiProxy.Stub
                    .asInterface(bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            MAIN_EXECUTOR.execute(TouchInteractionService.this::initInputMonitor);
            MAIN_EXECUTOR.execute(TouchInteractionService.this::onSystemUiProxySet);
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
            mAssistantAvailable = available;
        }

        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            mLastAssistantVisibility = visibility;
            MAIN_EXECUTOR.execute(
                    TouchInteractionService.this::onAssistantVisibilityChanged);
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
            mSystemUiStateFlags = stateFlags;
            MAIN_EXECUTOR.execute(TouchInteractionService.this::onSystemUiFlagsChanged);
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
    private ISystemUiProxy mISystemUiProxy;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private OverviewInteractionState mOverviewInteractionState;
    private OverviewCallbacks mOverviewCallbacks;
    private TaskOverlayFactory mTaskOverlayFactory;
    private InputConsumerController mInputConsumer;
    private boolean mAssistantAvailable;
    private float mLastAssistantVisibility = 0;
    private @SystemUiStateFlags int mSystemUiStateFlags;

    private boolean mIsUserUnlocked;
    private BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                initWhenUserUnlocked();
            }
        }
    };

    private InputConsumer mUncheckedConsumer = InputConsumer.NO_OP;
    private InputConsumer mConsumer = InputConsumer.NO_OP;
    private Choreographer mMainChoreographer;

    private Region mActiveNavBarRegion = new Region();

    private InputMonitorCompat mInputMonitorCompat;
    private InputEventReceiver mInputEventReceiver;
    private Mode mMode = Mode.THREE_BUTTONS;
    private int mDefaultDisplayId;
    private final RectF mSwipeTouchRegion = new RectF();
    private final RectF mAssistantLeftRegion = new RectF();
    private final RectF mAssistantRightRegion = new RectF();

    private ComponentName mGestureBlockingActivity;

    private Region mExclusionRegion;
    private SystemGestureExclusionListenerCompat mExclusionListener;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in initWhenUserUnlocked() below.
        mMainChoreographer = Choreographer.getInstance();
        mAM = ActivityManagerWrapper.getInstance();

        if (UserManagerCompat.getInstance(this).isUserUnlocked(Process.myUserHandle())) {
            initWhenUserUnlocked();
        } else {
            mIsUserUnlocked = false;
            registerReceiver(mUserUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }

        mDefaultDisplayId = DefaultDisplay.INSTANCE.get(this).getInfo().id;
        String blockingActivity = getString(R.string.gesture_blocking_activity);
        mGestureBlockingActivity = TextUtils.isEmpty(blockingActivity) ? null :
                ComponentName.unflattenFromString(blockingActivity);

        mExclusionListener = new SystemGestureExclusionListenerCompat(mDefaultDisplayId) {
            @Override
            @BinderThread
            public void onExclusionChanged(Region region) {
                // Assignments are atomic, it should be safe on binder thread
                mExclusionRegion = region;
            }
        };

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
        if (!mMode.hasGestures || mISystemUiProxy == null) {
            return;
        }
        disposeEventHandlers();
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "initInputMonitor 2");
        }

        try {
            mInputMonitorCompat = InputMonitorCompat.fromBundle(mISystemUiProxy
                    .monitorGestureInput("swipe-up", mDefaultDisplayId), KEY_EXTRA_INPUT_MONITOR);
            mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                    mMainChoreographer, this::onInputEvent);
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "initInputMonitor 3");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to create input monitor", e);
        }
        initTouchBounds();
    }

    private int getNavbarSize(String resName) {
        return ResourceUtils.getNavbarSize(resName, getResources());
    }

    private void initTouchBounds() {
        if (!mMode.hasGestures) {
            return;
        }

        DefaultDisplay.Info displayInfo = DefaultDisplay.INSTANCE.get(this).getInfo();
        Point realSize = new Point(displayInfo.realSize);
        mSwipeTouchRegion.set(0, 0, realSize.x, realSize.y);
        if (mMode == Mode.NO_BUTTON) {
            int touchHeight = getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
            mSwipeTouchRegion.top = mSwipeTouchRegion.bottom - touchHeight;

            final int assistantWidth = getResources()
                    .getDimensionPixelSize(R.dimen.gestures_assistant_width);
            final float assistantHeight = Math.max(touchHeight,
                    QuickStepContract.getWindowCornerRadius(getResources()));
            mAssistantLeftRegion.bottom = mAssistantRightRegion.bottom = mSwipeTouchRegion.bottom;
            mAssistantLeftRegion.top = mAssistantRightRegion.top =
                    mSwipeTouchRegion.bottom - assistantHeight;

            mAssistantLeftRegion.left = 0;
            mAssistantLeftRegion.right = assistantWidth;

            mAssistantRightRegion.right = mSwipeTouchRegion.right;
            mAssistantRightRegion.left = mSwipeTouchRegion.right - assistantWidth;
        } else {
            mAssistantLeftRegion.setEmpty();
            mAssistantRightRegion.setEmpty();
            switch (displayInfo.rotation) {
                case Surface.ROTATION_90:
                    mSwipeTouchRegion.left = mSwipeTouchRegion.right
                            - getNavbarSize(ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);
                    break;
                case Surface.ROTATION_270:
                    mSwipeTouchRegion.right = mSwipeTouchRegion.left
                            + getNavbarSize(ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);
                    break;
                default:
                    mSwipeTouchRegion.top = mSwipeTouchRegion.bottom
                            - getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
            }
        }
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "onNavigationModeChanged " + newMode);
        }
        if (mMode.hasGestures != newMode.hasGestures) {
            if (newMode.hasGestures) {
                DefaultDisplay.INSTANCE.get(this).addChangeListener(this);
            } else {
                DefaultDisplay.INSTANCE.get(this).removeChangeListener(this);
            }
        }
        mMode = newMode;

        disposeEventHandlers();
        initInputMonitor();

        if (mMode == Mode.NO_BUTTON) {
            mExclusionListener.register();
        } else {
            mExclusionListener.unregister();
        }
    }

    @Override
    public void onDisplayInfoChanged(DefaultDisplay.Info info, int flags) {
        if (info.id != mDefaultDisplayId) {
            return;
        }

        initTouchBounds();
    }

    private void initWhenUserUnlocked() {
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this);

        mOverviewCommandHelper = new OverviewCommandHelper(this, mOverviewComponentObserver);
        mOverviewInteractionState = OverviewInteractionState.INSTANCE.get(this);
        mOverviewCallbacks = OverviewCallbacks.get(this);
        mTaskOverlayFactory = TaskOverlayFactory.INSTANCE.get(this);
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();
        mIsUserUnlocked = true;

        sSwipeSharedState.setOverviewComponentObserver(mOverviewComponentObserver);
        mInputConsumer.registerInputConsumer();
        onSystemUiProxySet();
        onSystemUiFlagsChanged();
        onAssistantVisibilityChanged();

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        mBackGestureNotificationCounter = Math.max(0, Utilities.getDevicePrefs(this)
                .getInt(KEY_BACK_NOTIFICATION_COUNT, MAX_BACK_NOTIFICATION_COUNT));

        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
    }

    @UiThread
    private void onSystemUiProxySet() {
        if (mIsUserUnlocked) {
            mRecentsModel.setSystemUiProxy(mISystemUiProxy);
            mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);
        }
    }

    @UiThread
    private void onSystemUiFlagsChanged() {
        if (mIsUserUnlocked) {
            mOverviewInteractionState.setSystemUiStateFlags(mSystemUiStateFlags);
            mOverviewComponentObserver.onSystemUiStateChanged(mSystemUiStateFlags);
        }
    }

    @UiThread
    private void onAssistantVisibilityChanged() {
        if (mIsUserUnlocked) {
            mOverviewComponentObserver.getActivityControlHelper().onAssistantVisibilityChanged(
                    mLastAssistantVisibility);
        }
    }

    @Override
    public void onDestroy() {
        sIsInitialized = false;
        if (mIsUserUnlocked) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.onDestroy();
        }
        disposeEventHandlers();
        if (mMode.hasGestures) {
            DefaultDisplay.INSTANCE.get(this).removeChangeListener(this);
        }

        sConnected = false;
        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
        SysUINavigationMode.INSTANCE.get(this).removeModeChangeListener(this);
        mExclusionListener.unregister();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void onInputEvent(InputEvent ev) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_BACKGROUND_TO_OVERVIEW_TAG, "onInputEvent " + ev);
        }
        if (!(ev instanceof MotionEvent)) {
            Log.e(TAG, "Unknown event " + ev);
            return;
        }

        MotionEvent event = (MotionEvent) ev;
        if (event.getAction() == ACTION_DOWN) {
            mLogId = TOUCH_INTERACTION_LOG.generateAndSetLogId();
            sSwipeSharedState.setLogTraceId(mLogId);

            if (mSwipeTouchRegion.contains(event.getX(), event.getY())) {
                boolean useSharedState = mConsumer.useSharedSwipeState();
                mConsumer.onConsumerAboutToBeSwitched();
                mConsumer = newConsumer(useSharedState, event);
                TOUCH_INTERACTION_LOG.addLog("setInputConsumer", mConsumer.getType());
                mUncheckedConsumer = mConsumer;
            } else if (mIsUserUnlocked && mMode == Mode.NO_BUTTON
                    && canTriggerAssistantAction(event)) {
                // Do not change mConsumer as if there is an ongoing QuickSwitch gesture, we should
                // not interrupt it. QuickSwitch assumes that interruption can only happen if the
                // next gesture is also quick switch.
                mUncheckedConsumer =
                        new AssistantTouchConsumer(this, mISystemUiProxy,
                                mOverviewComponentObserver.getActivityControlHelper(),
                                InputConsumer.NO_OP, mInputMonitorCompat);
            } else {
                mUncheckedConsumer = InputConsumer.NO_OP;
            }
        }

        TOUCH_INTERACTION_LOG.addLog("onMotionEvent", event.getActionMasked());
        mUncheckedConsumer.onMotionEvent(event);
    }

    private boolean validSystemUiFlags() {
        return (mSystemUiStateFlags & SYSUI_STATE_NAV_BAR_HIDDEN) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_QUICK_SETTINGS_EXPANDED) == 0
                && ((mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED) == 0
                        || (mSystemUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0);
    }

    private boolean canTriggerAssistantAction(MotionEvent ev) {
        return mAssistantAvailable
                && !QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags)
                && (mAssistantLeftRegion.contains(ev.getX(), ev.getY()) ||
                    mAssistantRightRegion.contains(ev.getX(), ev.getY()))
                && !ActivityManagerWrapper.getInstance().isLockToAppActive();
    }

    private InputConsumer newConsumer(boolean useSharedState, MotionEvent event) {
        boolean isInValidSystemUiState = validSystemUiFlags();

        if (!mIsUserUnlocked) {
            if (isInValidSystemUiState) {
                // This handles apps launched in direct boot mode (e.g. dialer) as well as apps
                // launched while device is locked even after exiting direct boot mode (e.g. camera).
                return createDeviceLockedInputConsumer(mAM.getRunningTask(ACTIVITY_TYPE_ASSISTANT));
            } else {
                return mResetGestureInputConsumer;
            }
        }

        // When using sharedState, bypass systemState check as this is a followup gesture and the
        // first gesture started in a valid system state.
        InputConsumer base = isInValidSystemUiState || useSharedState
                ? newBaseConsumer(useSharedState, event) : mResetGestureInputConsumer;
        if (mMode == Mode.NO_BUTTON) {
            final ActivityControlHelper activityControl =
                    mOverviewComponentObserver.getActivityControlHelper();
            if (canTriggerAssistantAction(event)) {
                base = new AssistantTouchConsumer(this, mISystemUiProxy, activityControl, base,
                        mInputMonitorCompat);
            }

            if ((mSystemUiStateFlags & SYSUI_STATE_SCREEN_PINNING) != 0) {
                // Note: we only allow accessibility to wrap this, and it replaces the previous
                // base input consumer (which should be NO_OP anyway since topTaskLocked == true).
                base = new ScreenPinnedInputConsumer(this, mISystemUiProxy, activityControl);
            }

            if ((mSystemUiStateFlags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0) {
                base = new AccessibilityInputConsumer(this, mISystemUiProxy,
                        (mSystemUiStateFlags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0, base,
                        mInputMonitorCompat, mSwipeTouchRegion);
            }
        } else {
            if ((mSystemUiStateFlags & SYSUI_STATE_SCREEN_PINNING) != 0) {
                base = mResetGestureInputConsumer;
            }
        }
        return base;
    }

    private InputConsumer newBaseConsumer(boolean useSharedState, MotionEvent event) {
        RunningTaskInfo runningTaskInfo = mAM.getRunningTask(0);
        if (!useSharedState) {
            sSwipeSharedState.clearAllState(false /* finishAnimation */);
        }
        if ((mSystemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0) {
            // This handles apps showing over the lockscreen (e.g. camera)
            return createDeviceLockedInputConsumer(runningTaskInfo);
        }

        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();

        boolean forceOverviewInputConsumer = false;
        if (AssistantUtilities.isExcludedAssistant(runningTaskInfo)) {
            // In the case where we are in the excluded assistant state, ignore it and treat the
            // running activity as the task behind the assistant
            runningTaskInfo = mAM.getRunningTask(ACTIVITY_TYPE_ASSISTANT /* ignoreActivityType */);
            ComponentName homeComponent = mOverviewComponentObserver.getHomeIntent().getComponent();
            ComponentName runningComponent = runningTaskInfo.baseIntent.getComponent();
            forceOverviewInputConsumer =
                runningComponent != null && runningComponent.equals(homeComponent);
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
            return createOverviewInputConsumer(event, forceOverviewInputConsumer);
        } else if (ENABLE_QUICKSTEP_LIVE_TILE.get() && activityControl.isInLiveTileMode()) {
            return createOverviewInputConsumer(event, forceOverviewInputConsumer);
        } else if (mGestureBlockingActivity != null && runningTaskInfo != null
                && mGestureBlockingActivity.equals(runningTaskInfo.topActivity)) {
            return mResetGestureInputConsumer;
        } else {
            return createOtherActivityInputConsumer(event, runningTaskInfo);
        }
    }

    private boolean disableHorizontalSwipe(MotionEvent event) {
        // mExclusionRegion can change on binder thread, use a local instance here.
        Region exclusionRegion = mExclusionRegion;
        return mMode == Mode.NO_BUTTON && exclusionRegion != null
                && exclusionRegion.contains((int) event.getX(), (int) event.getY());
    }

    private InputConsumer createOtherActivityInputConsumer(MotionEvent event,
            RunningTaskInfo runningTaskInfo) {

        final boolean shouldDefer;
        final BaseSwipeUpHandler.Factory factory;

        if (mMode == Mode.NO_BUTTON && !mOverviewComponentObserver.isHomeAndOverviewSame()) {
            shouldDefer = !sSwipeSharedState.recentsAnimationFinishInterrupted;
            factory = mFallbackNoButtonFactory;
        } else {
            shouldDefer = mOverviewComponentObserver.getActivityControlHelper()
                    .deferStartingActivity(mActiveNavBarRegion, event);
            factory = mWindowTreansformFactory;
        }

        return new OtherActivityInputConsumer(this, runningTaskInfo,
                shouldDefer, mOverviewCallbacks, this::onConsumerInactive,
                sSwipeSharedState, mInputMonitorCompat, mSwipeTouchRegion,
                disableHorizontalSwipe(event), factory, mLogId);
    }

    private InputConsumer createDeviceLockedInputConsumer(RunningTaskInfo taskInfo) {
        if (mMode == Mode.NO_BUTTON && taskInfo != null) {
            return new DeviceLockedInputConsumer(this, sSwipeSharedState, mInputMonitorCompat,
                    mSwipeTouchRegion, taskInfo.taskId, mLogId);
        } else {
            return mResetGestureInputConsumer;
        }
    }

    public InputConsumer createOverviewInputConsumer(MotionEvent event,
            boolean forceOverviewInputConsumer) {
        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        BaseDraggingActivity activity = activityControl.getCreatedActivity();
        if (activity == null) {
            return mResetGestureInputConsumer;
        }

        if (activity.getRootView().hasWindowFocus()
                || sSwipeSharedState.goingToLauncher
                || (BaseFlags.ASSISTANT_GIVES_LAUNCHER_FOCUS.get()
                    && forceOverviewInputConsumer)) {
            return new OverviewInputConsumer(activity, mInputMonitorCompat,
                    false /* startingInActivityBounds */);
        } else {
            return new OverviewWithoutFocusInputConsumer(activity, mInputMonitorCompat,
                    disableHorizontalSwipe(event));
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
        if (!mIsUserUnlocked) {
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
        if (!mIsUserUnlocked) {
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
            pw.println("TouchState:");
            pw.println("  navMode=" + mMode);
            pw.println("  validSystemUiFlags=" + validSystemUiFlags());
            pw.println("  systemUiFlags=" + mSystemUiStateFlags);
            pw.println("  systemUiFlagsDesc="
                    + QuickStepContract.getSystemUiStateString(mSystemUiStateFlags));
            pw.println("  assistantAvailable=" + mAssistantAvailable);
            pw.println("  assistantDisabled="
                    + QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags));
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
            TOUCH_INTERACTION_LOG.dump("", pw);

        }
    }

    private void printAvailableCommands(PrintWriter pw) {
        pw.println("Available commands:");
        pw.println("  clear-touch-log: Clears the touch interaction log");
    }

    private void onCommand(PrintWriter pw, ArgList args) {
        switch (args.nextArg()) {
            case "clear-touch-log":
                TOUCH_INTERACTION_LOG.clear();
                break;
        }
    }

    private BaseSwipeUpHandler createWindowTransformSwipeHandler(RunningTaskInfo runningTask,
            long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask) {
        return  new WindowTransformSwipeHandler(runningTask, this, touchTimeMs,
                mOverviewComponentObserver, continuingLastGesture, mInputConsumer, mRecentsModel);
    }

    private BaseSwipeUpHandler createFallbackNoButtonSwipeHandler(RunningTaskInfo runningTask,
            long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask) {
        return new FallbackNoButtonInputConsumer(this, mOverviewComponentObserver, runningTask,
                mRecentsModel, mInputConsumer, isLikelyToStartNewTask, continuingLastGesture);
    }

    protected boolean shouldNotifyBackGesture() {
        return mBackGestureNotificationCounter > 0 &&
                mGestureBlockingActivity != null;
    }

    @WorkerThread
    protected void tryNotifyBackGesture() {
        if (shouldNotifyBackGesture()) {
            mBackGestureNotificationCounter--;
            Utilities.getDevicePrefs(this).edit()
                    .putInt(KEY_BACK_NOTIFICATION_COUNT, mBackGestureNotificationCounter).apply();
            sendBroadcast(new Intent(NOTIFY_ACTION_BACK).setPackage(
                    mGestureBlockingActivity.getPackageName()));
        }
    }

    public static void startRecentsActivityAsync(Intent intent, RecentsAnimationListener listener) {
        UI_HELPER_EXECUTOR.execute(() -> ActivityManagerWrapper.getInstance()
                .startRecentsActivity(intent, null, listener, null, null));
    }
}
