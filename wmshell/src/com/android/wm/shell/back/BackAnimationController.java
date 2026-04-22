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

package com.android.wm.shell.back;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION;
import static android.window.BackEvent.EDGE_NONE;
import static android.window.DesktopExperienceFlags.ENABLE_INDEPENDENT_BACK_IN_PROJECTED;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;
import static android.window.TransitionInfo.FLAG_SHOW_WALLPAPER;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_PREDICTIVE_BACK_HOME;
import static com.android.systemui.Flags.predictiveBackDelayWmTransition;
import static com.android.window.flags2.Flags.unifyBackNavigationTransition;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.input.KeyGestureEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.IRemoteAnimationRunner;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.BackAnimationAdapter;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackNavigationInfo;
import android.window.BackTouchTracker;
import android.window.IBackAnimationFinishedCallback;
import android.window.IBackAnimationHandoffHandler;
import android.window.IBackAnimationRunner;
import android.window.IOnBackInvokedCallback;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.LatencyTracker;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.animation.TransitionAnimator;
import com.android.window.flags2.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Controls the window animation run when a user initiates a back gesture.
 */
public class BackAnimationController implements RemoteCallable<BackAnimationController>,
        ConfigurationChangeListener {
    private static final String TAG = "ShellBackPreview";

    /**
     * Max duration to wait for an animation to finish before triggering the real back.
     */
    private static final long MAX_ANIMATION_DURATION = 2000;
    private final LatencyTracker mLatencyTracker;
    @ShellMainThread private final Handler mHandler;

    /** True when a back gesture is ongoing */
    @VisibleForTesting public boolean mBackGestureStarted = false;

    /** Tracks if an uninterruptible animation is in progress */
    private boolean mPostCommitAnimationInProgress = false;
    private boolean mRealCallbackInvoked = false;

    /** Tracks if we should start the back gesture on the next motion move event */
    private boolean mShouldStartOnNextMoveEvent = false;
    private boolean mOnBackStartDispatched = false;
    private boolean mThresholdCrossed = false;
    private boolean mPointersPilfered = false;
    private final boolean mRequirePointerPilfer;

    /** Registry for the back animations */
    private final ShellBackAnimationRegistry mShellBackAnimationRegistry;

    @Nullable
    private BackNavigationInfo mBackNavigationInfo;
    private boolean mReceivedNullNavigationInfo = false;
    private final IActivityTaskManager mActivityTaskManager;
    private final Context mContext;
    private final ShellController mShellController;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellExecutor mShellExecutor;
    private final WindowManager mWindowManager;
    private final Transitions mTransitions;
    private final InputManager mInputManager;
    @VisibleForTesting
    final BackTransitionHandler mBackTransitionHandler;
    @VisibleForTesting
    final Rect mTouchableArea = new Rect();

    /**
     * Tracks the current user back gesture.
     */
    private BackTouchTracker mCurrentTracker = new BackTouchTracker();

    /**
     * Tracks the next back gesture in case a new user gesture has started while the back animation
     * (and navigation) associated with {@link #mCurrentTracker} have not yet finished.
     */
    private BackTouchTracker mQueuedTracker = new BackTouchTracker();

    private final BackTransitionObserver mBackTransitionObserver =
            new BackTransitionObserver();

    private final Runnable mAnimationTimeoutRunnable = () -> {
        ProtoLog.w(WM_SHELL_BACK_PREVIEW, "Animation didn't finish in %d ms. Resetting...",
                MAX_ANIMATION_DURATION);
        finishBackAnimation();
    };

    private IBackAnimationFinishedCallback mBackAnimationFinishedCallback;
    @VisibleForTesting
    BackAnimationAdapter mBackAnimationAdapter;

    @Nullable
    private IOnBackInvokedCallback mActiveCallback;
    @Nullable
    @VisibleForTesting
    RemoteAnimationTarget[] mApps;

    @VisibleForTesting
    final RemoteCallback mNavigationObserver = new RemoteCallback(
            new RemoteCallback.OnResultListener() {
                @Override
                public void onResult(@Nullable Bundle result) {
                    mShellExecutor.execute(() -> {
                        if (mBackGestureStarted && result != null && result.getBoolean(
                                BackNavigationInfo.KEY_TOUCH_GESTURE_TRANSFERRED)) {
                            // Host app won't able to process motion event anymore, so pilfer
                            // pointers anyway.
                            if (mBackNavigationInfo != null) {
                                mBackNavigationInfo.disableAppProgressGenerationAllowed();
                            }
                            tryPilferPointers();
                            return;
                        }
                        if (!mBackGestureStarted || mPostCommitAnimationInProgress) {
                            // If an uninterruptible animation is already in progress, we should
                            // ignore this due to it may cause focus lost. (alpha = 0)
                            return;
                        }
                        ProtoLog.i(WM_SHELL_BACK_PREVIEW, "Navigation window gone.");
                        setTriggerBack(false);
                        // Trigger close transition if necessary.
                        mBackTransitionHandler.onAnimationFinished();
                        resetTouchTracker();
                        // Don't wait for animation start
                        mShellExecutor.removeCallbacks(mAnimationTimeoutRunnable);
                    });
                }
            });

    private final BackAnimationBackground mAnimationBackground;
    private StatusBarCustomizer mCustomizer;
    private boolean mTrackingLatency;

    // Keep previous navigation type before remove mBackNavigationInfo.
    @BackNavigationInfo.BackTargetType
    private int mPreviousNavigationType;
    private Runnable mPilferPointerCallback;
    private BackAnimation.TopUiRequest mRequestTopUiCallback;

    private final IBackAnimationHandoffHandler mHandoffHandler =
            new IBackAnimationHandoffHandler.Stub() {
                @Override
                public void handOffAnimation(
                        RemoteAnimationTarget[] targets, WindowAnimationState[] states) {
                    mBackTransitionHandler.handOffAnimation(targets, states);
                }
            };

    public BackAnimationController(
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull @ShellMainThread ShellExecutor shellExecutor,
            Context context,
            @NonNull BackAnimationBackground backAnimationBackground,
            ShellBackAnimationRegistry shellBackAnimationRegistry,
            ShellCommandHandler shellCommandHandler,
            Transitions transitions,
            @ShellMainThread Handler handler) {
        this(
                shellInit,
                shellController,
                shellExecutor,
                ActivityTaskManager.getService(),
                context,
                backAnimationBackground,
                shellBackAnimationRegistry,
                shellCommandHandler,
                transitions,
                handler);
    }

    @VisibleForTesting
    BackAnimationController(
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull @ShellMainThread ShellExecutor shellExecutor,
            @NonNull IActivityTaskManager activityTaskManager,
            Context context,
            @NonNull BackAnimationBackground backAnimationBackground,
            ShellBackAnimationRegistry shellBackAnimationRegistry,
            ShellCommandHandler shellCommandHandler,
            Transitions transitions,
            @NonNull @ShellMainThread Handler handler) {
        mShellController = shellController;
        mShellExecutor = shellExecutor;
        mActivityTaskManager = activityTaskManager;
        mContext = context;
        mRequirePointerPilfer =
                context.getResources().getBoolean(R.bool.config_backAnimationRequiresPointerPilfer);
        shellInit.addInitCallback(this::onInit, this);
        mAnimationBackground = backAnimationBackground;
        mShellBackAnimationRegistry = shellBackAnimationRegistry;
        mLatencyTracker = LatencyTracker.getInstance(mContext);
        mShellCommandHandler = shellCommandHandler;
        mWindowManager = context.getSystemService(WindowManager.class);
        mInputManager = context.getSystemService(InputManager.class);
        mTransitions = transitions;
        mBackTransitionHandler = new BackTransitionHandler(mTransitions);
        mTransitions.addHandler(mBackTransitionHandler);
        mHandler = handler;
        mTransitions.registerObserver(mBackTransitionObserver);
        mBackTransitionObserver.setBackTransitionHandler(mBackTransitionHandler);
        updateTouchableArea();
    }

    private void onInit() {
        createAdapter();
        mShellController.addExternalInterface(IBackAnimation.DESCRIPTOR,
                this::createExternalInterface, this);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mShellController.addConfigurationChangeListener(this);
        registerBackGestureDelegate();
    }

    public BackAnimation getBackAnimationImpl() {
        return mBackAnimation;
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IBackAnimationImpl(this);
    }

    private final BackAnimationImpl mBackAnimation = new BackAnimationImpl();

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mShellBackAnimationRegistry.onConfigurationChanged(newConfig);
        updateTouchableArea();
    }

    private void updateTouchableArea() {
        mTouchableArea.set(mWindowManager.getCurrentWindowMetrics().getBounds());
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mShellExecutor;
    }

    private class BackAnimationImpl implements BackAnimation {
        @Override
        public void onBackMotion(
                float touchX,
                float touchY,
                int keyAction,
                @BackEvent.SwipeEdge int swipeEdge,
                int displayId
        ) {
            mShellExecutor.execute(
                    () -> onMotionEvent(touchX, touchY, keyAction, swipeEdge, displayId));
        }

        @Override
        public void onThresholdCrossed() {
            if (predictiveBackDelayWmTransition()) {
                mShellExecutor.execute(BackAnimationController.this::onThresholdCrossed);
            } else {
                BackAnimationController.this.onThresholdCrossed();
            }
        }

        @Override
        public void setTriggerBack(boolean triggerBack) {
            mShellExecutor.execute(() -> BackAnimationController.this.setTriggerBack(triggerBack));
        }

        @Override
        public void setSwipeThresholds(
                float linearDistance,
                float maxDistance,
                float nonLinearFactor) {
            mShellExecutor.execute(() -> BackAnimationController.this.setSwipeThresholds(
                    linearDistance, maxDistance, nonLinearFactor));
        }

        @Override
        public void setStatusBarCustomizer(StatusBarCustomizer customizer) {
            mCustomizer = customizer;
            mAnimationBackground.setStatusBarCustomizer(customizer);
        }

        @Override
        public void setPilferPointerCallback(Runnable callback) {
            mShellExecutor.execute(() -> {
                mPilferPointerCallback = callback;
            });
        }

        @Override
        public void setTopUiRequestCallback(TopUiRequest topUiRequest) {
            mShellExecutor.execute(() -> mRequestTopUiCallback = topUiRequest);
        }
    }

    private class IBackAnimationImpl extends IBackAnimation.Stub
            implements ExternalInterfaceBinder {
        private BackAnimationController mController;

        IBackAnimationImpl(BackAnimationController controller) {
            mController = controller;
        }

        @Override
        public void setBackToLauncherCallback(IOnBackInvokedCallback callback,
                IRemoteAnimationRunner runner) {
            executeRemoteCallWithTaskPermission(mController, "setBackToLauncherCallback",
                    (controller) -> controller.registerAnimation(
                            BackNavigationInfo.TYPE_RETURN_TO_HOME,
                            new BackAnimationRunner(
                                    callback,
                                    runner,
                                    controller.mContext,
                                    CUJ_PREDICTIVE_BACK_HOME,
                                    mHandler)));
        }

        @Override
        public void clearBackToLauncherCallback() {
            executeRemoteCallWithTaskPermission(mController, "clearBackToLauncherCallback",
                    (controller) -> controller.unregisterAnimation(
                            BackNavigationInfo.TYPE_RETURN_TO_HOME));
        }

        public void customizeStatusBarAppearance(AppearanceRegion appearance) {
            executeRemoteCallWithTaskPermission(mController, "useLauncherSysBarFlags",
                    (controller) -> controller.customizeStatusBarAppearance(appearance));
        }

        @Override
        public void invalidate() {
            mController = null;
        }
    }

    private void customizeStatusBarAppearance(AppearanceRegion appearance) {
        if (mCustomizer != null) {
            mCustomizer.customizeStatusBarAppearance(appearance);
        }
    }

    void registerAnimation(@BackNavigationInfo.BackTargetType int type,
            @NonNull BackAnimationRunner runner) {
        mShellBackAnimationRegistry.registerAnimation(type, runner);
    }

    void unregisterAnimation(@BackNavigationInfo.BackTargetType int type) {
        mShellBackAnimationRegistry.unregisterAnimation(type);
    }

    private BackTouchTracker getActiveTracker() {
        if (mCurrentTracker.isActive()) return mCurrentTracker;
        if (mQueuedTracker.isActive()) return mQueuedTracker;
        return null;
    }

    @VisibleForTesting
    public void onThresholdCrossed() {
        mThresholdCrossed = true;
        BackTouchTracker activeTracker = getActiveTracker();
        if (predictiveBackDelayWmTransition() && activeTracker != null && mActiveCallback == null
                && mBackGestureStarted) {
            startBackNavigation(activeTracker);
        }
        // There was no focus window when calling startBackNavigation, still pilfer pointers so
        // the next focus window won't receive motion events.
        if (mBackNavigationInfo == null && mReceivedNullNavigationInfo) {
            tryPilferPointers();
            return;
        }
        // Dispatch onBackStarted, only to app callbacks.
        // System callbacks will receive onBackStarted when the remote animation starts.
        final boolean shouldDispatchToAnimator = shouldDispatchToAnimator();
        if (!shouldDispatchToAnimator && mActiveCallback != null) {
            mCurrentTracker.updateStartLocation();
            tryDispatchOnBackStarted(mActiveCallback, mCurrentTracker.createStartEvent(null));
            if (mBackNavigationInfo != null && !isAppProgressGenerationAllowed()) {
                tryPilferPointers();
            }
        } else if (shouldDispatchToAnimator) {
            tryPilferPointers();
        }
    }

    private boolean isAppProgressGenerationAllowed() {
        return mBackNavigationInfo.isAppProgressGenerationAllowed()
                && mBackNavigationInfo.getTouchableRegion().equals(mTouchableArea);
    }

    /**
     * Called when a new motion event needs to be transferred to this
     * {@link BackAnimationController}
     */
    public void onMotionEvent(
            float touchX,
            float touchY,
            int keyAction,
            @BackEvent.SwipeEdge int swipeEdge,
            int displayId) {

        if (ENABLE_INDEPENDENT_BACK_IN_PROJECTED.isTrue()) {
            mBackAnimationAdapter.mOriginDisplayId = displayId;
        }

        BackTouchTracker activeTouchTracker = getActiveTracker();
        if (activeTouchTracker != null) {
            activeTouchTracker.update(touchX, touchY);
        }

        // two gestures are waiting to be processed at the moment, skip any further user touches
        if (mCurrentTracker.isFinished() && mQueuedTracker.isFinished()) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "Ignoring MotionEvent because two gestures are already being queued.");
            return;
        } else if (mBackGestureStarted && mCurrentTracker.isInInitialState()
                && mQueuedTracker.isInInitialState()) {
            ProtoLog.e(WM_SHELL_BACK_PREVIEW,
                    "Both touch trackers in initial state and mBackGestureStarted=true");
            mBackGestureStarted = false;
        }

        if (keyAction == MotionEvent.ACTION_DOWN) {
            if (!mBackGestureStarted) {
                if (swipeEdge == EDGE_NONE) {
                    // start animation immediately for non-gestural sources (without ACTION_MOVE
                    // events)
                    if (!predictiveBackDelayWmTransition()) {
                        mThresholdCrossed = true;
                    }
                    mPointersPilfered = true;
                    onGestureStarted(touchX, touchY, swipeEdge);
                    if (predictiveBackDelayWmTransition()) {
                        onThresholdCrossed();
                    }
                    mShouldStartOnNextMoveEvent = false;
                } else {
                    if (predictiveBackDelayWmTransition()) {
                        onGestureStarted(touchX, touchY, swipeEdge);
                    } else {
                        mShouldStartOnNextMoveEvent = true;
                    }
                }
            }
        } else if (keyAction == MotionEvent.ACTION_MOVE) {
            if (!mBackGestureStarted && mShouldStartOnNextMoveEvent) {
                // Let the animation initialized here to make sure the onPointerDownOutsideFocus
                // could be happened when ACTION_DOWN, it may change the current focus that we
                // would access it when startBackNavigation.
                onGestureStarted(touchX, touchY, swipeEdge);
                mShouldStartOnNextMoveEvent = false;
            }
            onMove(swipeEdge);
        } else if (keyAction == MotionEvent.ACTION_UP || keyAction == MotionEvent.ACTION_CANCEL) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "Finishing gesture with event action: %d", keyAction);
            if (keyAction == MotionEvent.ACTION_CANCEL) {
                setTriggerBack(false);
            }
            onGestureFinished();
        }
    }

    private void onGestureStarted(float touchX, float touchY, @BackEvent.SwipeEdge int swipeEdge) {
        boolean interruptCancelPostCommitAnimation = mPostCommitAnimationInProgress
                && mCurrentTracker.isFinished() && !mCurrentTracker.getTriggerBack()
                && mQueuedTracker.isInInitialState();
        if (interruptCancelPostCommitAnimation) {
            // If a system animation is currently in the post-commit phase animating an
            // onBackCancelled event, let's interrupt it and start animating a new back gesture
            resetTouchTracker();
        }
        BackTouchTracker touchTracker;
        if (mCurrentTracker.isInInitialState()) {
            touchTracker = mCurrentTracker;
        } else if (mQueuedTracker.isInInitialState()) {
            touchTracker = mQueuedTracker;
        } else {
            ProtoLog.w(WM_SHELL_BACK_PREVIEW,
                    "Cannot start tracking new gesture with neither tracker in initial state.");
            return;
        }
        touchTracker.setGestureStartLocation(touchX, touchY, swipeEdge);
        touchTracker.setState(BackTouchTracker.TouchTrackerState.ACTIVE);
        mBackGestureStarted = true;

        if (interruptCancelPostCommitAnimation) {
            // post-commit cancel is currently running. let's interrupt it and dispatch a new
            // onBackStarted event.
            mPostCommitAnimationInProgress = false;
            mShellExecutor.removeCallbacks(mAnimationTimeoutRunnable);
            startSystemAnimation();
        } else if (!predictiveBackDelayWmTransition()) {
            startBackNavigation(touchTracker);
        }
    }

    private void startBackNavigation(@NonNull BackTouchTracker touchTracker) {
        if (touchTracker != mCurrentTracker) {
            // Only start the back navigation if no other gesture is being processed. Otherwise,
            // the back navigation will fall back to legacy back event injection.
            return;
        }
        try {
            startLatencyTracking();
            if (mBackAnimationAdapter != null
                    && mShellBackAnimationRegistry.hasSupportedAnimatorsChanged()) {
                mBackAnimationAdapter.updateSupportedAnimators(
                        mShellBackAnimationRegistry.getSupportedAnimators());
            }
            mBackNavigationInfo = mActivityTaskManager.startBackNavigation(
                    mNavigationObserver, mBackAnimationAdapter);
            onBackNavigationInfoReceived(mBackNavigationInfo, touchTracker);
        } catch (RemoteException remoteException) {
            Log.e(TAG, "Failed to initAnimation", remoteException);
            finishBackNavigation(touchTracker.getTriggerBack());
        }
    }

    private void onBackNavigationInfoReceived(@Nullable BackNavigationInfo backNavigationInfo,
            @NonNull BackTouchTracker touchTracker) {
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Received backNavigationInfo:%s", backNavigationInfo);
        if (backNavigationInfo == null) {
            ProtoLog.e(WM_SHELL_BACK_PREVIEW, "Received BackNavigationInfo is null.");
            mReceivedNullNavigationInfo = true;
            cancelLatencyTracking();
            tryPilferPointers();
            return;
        }
        final int backType = backNavigationInfo.getType();
        final boolean shouldDispatchToAnimator = shouldDispatchToAnimator();
        if (shouldDispatchToAnimator) {
            if (!mShellBackAnimationRegistry.startGesture(backType)) {
                mActiveCallback = null;
            }
            requestTopUi(true, backType);
            tryPilferPointers();
        } else {
            mActiveCallback = mBackNavigationInfo.getOnBackInvokedCallback();
            // App is handling back animation. Cancel system animation latency tracking.
            cancelLatencyTracking();
            tryDispatchOnBackStarted(mActiveCallback, touchTracker.createStartEvent(null));
            if (!isAppProgressGenerationAllowed()) {
                tryPilferPointers();
            }
        }
    }

    private void onMove(@BackEvent.SwipeEdge int swipeEdge) {
        if (predictiveBackDelayWmTransition() && mCurrentTracker.isActive()) {
            mCurrentTracker.updateSwipeEdge(swipeEdge);
        }
        if (!mBackGestureStarted
                || mBackNavigationInfo == null
                || mActiveCallback == null
                || !mOnBackStartDispatched) {
            return;
        }
        // Skip dispatching if the move corresponds to the queued instead of the current gesture
        if (mQueuedTracker.isActive()) return;
        final BackMotionEvent backEvent = mCurrentTracker.createProgressEvent();
        dispatchOnBackProgressed(mActiveCallback, backEvent);
    }

    private void injectBackKey(int displayId) {
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "injectBackKey");
        sendBackEvent(KeyEvent.ACTION_DOWN, displayId);
        sendBackEvent(KeyEvent.ACTION_UP, displayId);
    }

    @SuppressLint("MissingPermission")
    private void sendBackEvent(int action, int displayId) {
        final long when = SystemClock.uptimeMillis();
        final KeyEvent ev = new KeyEvent(when, when, action, KeyEvent.KEYCODE_BACK, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        if (ENABLE_INDEPENDENT_BACK_IN_PROJECTED.isTrue()) {
            ev.setDisplayId(displayId);
        }

        if (!mContext.getSystemService(InputManager.class)
                .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
            ProtoLog.e(WM_SHELL_BACK_PREVIEW, "Inject input event fail");
        }
    }

    private boolean shouldDispatchToAnimator() {
        return mBackNavigationInfo != null && mBackNavigationInfo.isPrepareRemoteAnimation();
    }

    private void tryPilferPointers() {
        if (mPointersPilfered || !mThresholdCrossed) {
            return;
        }
        if (mPilferPointerCallback != null) {
            mPilferPointerCallback.run();
        }
        mPointersPilfered = true;
    }

    private void tryDispatchOnBackStarted(
            IOnBackInvokedCallback callback,
            BackMotionEvent backEvent) {
        if (mOnBackStartDispatched
                || callback == null
                || (!mThresholdCrossed && mRequirePointerPilfer)) {
            return;
        }
        dispatchOnBackStarted(callback, backEvent);
    }

    private void dispatchOnBackStarted(
            IOnBackInvokedCallback callback,
            BackMotionEvent backEvent) {
        if (callback == null) {
            return;
        }
        try {
            callback.onBackStarted(backEvent);
            if (mBackTransitionHandler.canHandOffAnimation()) {
                callback.setHandoffHandler(mHandoffHandler);
            } else {
                callback.setHandoffHandler(null);
            }
            mOnBackStartDispatched = true;
        } catch (RemoteException e) {
            Log.e(TAG, "dispatchOnBackStarted error: ", e);
        }
    }

    private void dispatchOnBackInvoked(IOnBackInvokedCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            callback.onBackInvoked();
        } catch (RemoteException e) {
            Log.e(TAG, "dispatchOnBackInvoked error: ", e);
        }
    }

    private void tryDispatchOnBackCancelled(IOnBackInvokedCallback callback) {
        if (!mOnBackStartDispatched) {
            Log.d(TAG, "Skipping dispatching onBackCancelled. Start was never dispatched.");
            return;
        }
        if (callback == null) {
            return;
        }
        try {
            callback.onBackCancelled();
        } catch (RemoteException e) {
            Log.e(TAG, "dispatchOnBackCancelled error: ", e);
        }
    }

    private void dispatchOnBackProgressed(IOnBackInvokedCallback callback,
            BackMotionEvent backEvent) {
        if (callback == null || (!shouldDispatchToAnimator() && mBackNavigationInfo != null
                && isAppProgressGenerationAllowed())) {
            return;
        }
        try {
            callback.onBackProgressed(backEvent);
        } catch (RemoteException e) {
            Log.e(TAG, "dispatchOnBackProgressed error: ", e);
        }
    }

    /**
     * @return Latest task id which back gesture has occurred on it.
     */
    public int getLatestTriggerBackTask() {
        return mBackTransitionObserver.mFocusedTaskId;
    }

    /**
     * Sets to true when the back gesture has passed the triggering threshold, false otherwise.
     */
    public void setTriggerBack(boolean triggerBack) {
        if (mActiveCallback != null) {
            try {
                mActiveCallback.setTriggerBack(triggerBack);
            } catch (RemoteException e) {
                Log.e(TAG, "remote setTriggerBack error: ", e);
            }
        }
        BackTouchTracker activeBackGestureInfo = getActiveTracker();
        if (activeBackGestureInfo != null) {
            activeBackGestureInfo.setTriggerBack(triggerBack);
        }
    }

    private void setSwipeThresholds(
            float linearDistance,
            float maxDistance,
            float nonLinearFactor) {
        mCurrentTracker.setProgressThresholds(linearDistance, maxDistance, nonLinearFactor);
        mQueuedTracker.setProgressThresholds(linearDistance, maxDistance, nonLinearFactor);
    }

    private void invokeOrCancelBack(@NonNull BackTouchTracker touchTracker) {
        // Make a synchronized call to core before dispatch back event to client side.
        // If the close transition happens before the core receives onAnimationFinished, there will
        // play a second close animation for that transition.
        if (mBackAnimationFinishedCallback != null) {
            try {
                mBackAnimationFinishedCallback.onAnimationFinished(touchTracker.getTriggerBack());
            } catch (RemoteException e) {
                Log.e(TAG, "Failed call IBackAnimationFinishedCallback", e);
            }
            mBackAnimationFinishedCallback = null;
        }

        if (mBackNavigationInfo != null && !mRealCallbackInvoked) {
            final IOnBackInvokedCallback callback = mBackNavigationInfo.getOnBackInvokedCallback();
            if (touchTracker.getTriggerBack()) {
                dispatchOnBackInvoked(callback);
            } else {
                tryDispatchOnBackCancelled(callback);
            }
        }
        mRealCallbackInvoked = false;
        finishBackNavigation(touchTracker.getTriggerBack());
    }

    /**
     * Called when the gesture is released, then it could start the post commit animation.
     */
    private void onGestureFinished() {
        BackTouchTracker activeTouchTracker = getActiveTracker();
        if (!mBackGestureStarted || activeTouchTracker == null) {
            // This can happen when an unfinished gesture has been reset in resetTouchTracker
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "onGestureFinished called while no gesture is started");
            return;
        }
        boolean triggerBack = activeTouchTracker.getTriggerBack();
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "onGestureFinished() mTriggerBack == %s", triggerBack);

        if (triggerBack) {
            mBackTransitionObserver.update(mBackNavigationInfo != null
                            ? mBackNavigationInfo.getFocusedTaskId()
                            : INVALID_TASK_ID);
        }
        // Reset gesture states.
        mThresholdCrossed = false;
        mPointersPilfered = false;
        mBackGestureStarted = false;
        activeTouchTracker.setState(BackTouchTracker.TouchTrackerState.FINISHED);

        if (mPostCommitAnimationInProgress) {
            ProtoLog.w(WM_SHELL_BACK_PREVIEW, "Animation is still running");
            return;
        }

        if (mBackNavigationInfo == null) {
            // No focus window found or core are running recents animation, inject back key as
            // legacy behavior, or new back gesture was started while previous has not finished yet
            if (!mQueuedTracker.isInInitialState()) {
                ProtoLog.e(WM_SHELL_BACK_PREVIEW, "mBackNavigationInfo is null AND there is "
                        + "another back animation in progress");
            }
            mCurrentTracker.reset();
            if (triggerBack) {
                injectBackKey(mBackAnimationAdapter.mOriginDisplayId);
            }
            finishBackNavigation(triggerBack);
            return;
        }

        final int backType = mBackNavigationInfo.getType();
        // Simply trigger and finish back navigation when no animator defined.
        if (!shouldDispatchToAnimator()
                || mShellBackAnimationRegistry.isAnimationCancelledOrNull(backType)) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Trigger back without dispatching to animator.");
            invokeOrCancelBack(mCurrentTracker);
            mCurrentTracker.reset();
            return;
        } else if (mShellBackAnimationRegistry.isWaitingAnimation(backType)) {
            ProtoLog.w(WM_SHELL_BACK_PREVIEW, "Gesture released, but animation didn't ready.");
            // Supposed it is in post commit animation state, and start the timeout to watch
            // if the animation is ready.
            mShellExecutor.executeDelayed(mAnimationTimeoutRunnable, MAX_ANIMATION_DURATION);
            return;
        }
        startPostCommitAnimation();
    }

    /**
     * Start the phase 2 animation when gesture is released.
     * Callback to {@link #onBackAnimationFinished} when it is finished or timeout.
     */
    private void startPostCommitAnimation() {
        if (mPostCommitAnimationInProgress) {
            return;
        }

        mShellExecutor.removeCallbacks(mAnimationTimeoutRunnable);
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "BackAnimationController: startPostCommitAnimation()");
        mPostCommitAnimationInProgress = true;
        mShellExecutor.executeDelayed(mAnimationTimeoutRunnable, MAX_ANIMATION_DURATION);

        // The next callback should be {@link #onBackAnimationFinished}.
        if (mCurrentTracker.getTriggerBack()) {
            // notify core gesture is commit
            if (shouldTriggerCloseTransition()) {
                mBackTransitionHandler.mCloseTransitionRequested = true;
                final IOnBackInvokedCallback callback =
                        mBackNavigationInfo.getOnBackInvokedCallback();
                // invoked client side onBackInvoked
                dispatchOnBackInvoked(callback);
                mRealCallbackInvoked = true;
            }
            // start post animation
            dispatchOnBackInvoked(mActiveCallback);
        } else {
            tryDispatchOnBackCancelled(mActiveCallback);
        }
    }

    // Close window won't create any transition
    private boolean shouldTriggerCloseTransition() {
        if (mBackNavigationInfo == null) {
            return false;
        }
        int type = mBackNavigationInfo.getType();
        return type == BackNavigationInfo.TYPE_RETURN_TO_HOME
                || type == BackNavigationInfo.TYPE_CROSS_TASK
                || type == BackNavigationInfo.TYPE_CROSS_ACTIVITY;
    }
    /**
     * Called when the post commit animation is completed or timeout.
     * This will trigger the real {@link IOnBackInvokedCallback} behavior.
     */
    @VisibleForTesting
    void onBackAnimationFinished() {
        if (!mPostCommitAnimationInProgress) {
            // This can happen when a post-commit cancel animation was interrupted by a new back
            // gesture but the timing of interruption was bad such that the back-callback
            // implementation finished in between the time of the new gesture having started and
            // the time of the back-callback receiving the new onBackStarted event. Due to the
            // asynchronous APIs this isn't an unlikely case. To handle this, let's return early.
            // The back-callback implementation will call onBackAnimationFinished again when it is
            // done with animating the second gesture.
            return;
        }
        finishBackAnimation();
    }

    private void finishBackAnimation() {
        // Stop timeout runner.
        mShellExecutor.removeCallbacks(mAnimationTimeoutRunnable);
        mPostCommitAnimationInProgress = false;

        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "BackAnimationController: onBackAnimationFinished()");

        if (mCurrentTracker.isActive() || mCurrentTracker.isFinished()) {
            // Trigger the real back.
            invokeOrCancelBack(mCurrentTracker);
        } else {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "mCurrentBackGestureInfo was null when back animation finished");
        }
        resetTouchTracker();
        mBackTransitionHandler.onAnimationFinished();
    }

    /**
     * Resets the BackTouchTracker and potentially starts a new back navigation in case one
     * is queued.
     */
    private void resetTouchTracker() {
        BackTouchTracker temp = mCurrentTracker;
        mCurrentTracker = mQueuedTracker;
        temp.reset();
        mQueuedTracker = temp;

        if (mCurrentTracker.isInInitialState()) {
            if (mBackGestureStarted) {
                mBackGestureStarted = false;
                tryDispatchOnBackCancelled(mActiveCallback);
                finishBackNavigation(false);
                ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                        "resetTouchTracker -> reset an unfinished gesture");
            } else {
                ProtoLog.d(WM_SHELL_BACK_PREVIEW, "resetTouchTracker -> no queued gesture");
            }
            return;
        }

        if (mCurrentTracker.isFinished() && mCurrentTracker.getTriggerBack()) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "resetTouchTracker -> start queued back navigation "
                    + "AND post commit animation");
            injectBackKey(mBackAnimationAdapter.mOriginDisplayId);
            finishBackNavigation(true);
            mCurrentTracker.reset();
        } else if (!mCurrentTracker.isFinished()) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "resetTouchTracker -> queued gesture not finished; do nothing");
        } else {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "resetTouchTracker -> reset queued gesture");
            mCurrentTracker.reset();
        }
    }

    /**
     * This should be called after the whole back navigation is completed.
     */
    @VisibleForTesting
    void finishBackNavigation(boolean triggerBack) {
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "BackAnimationController: finishBackNavigation()");
        mActiveCallback = null;
        mApps = null;
        mOnBackStartDispatched = false;
        mThresholdCrossed = false;
        mPointersPilfered = false;
        mShellBackAnimationRegistry.resetDefaultCrossActivity();
        cancelLatencyTracking();
        mReceivedNullNavigationInfo = false;
        if (mBackNavigationInfo != null) {
            mPreviousNavigationType = mBackNavigationInfo.getType();
            mBackNavigationInfo.onBackNavigationFinished(triggerBack);
            mBackNavigationInfo = null;
            requestTopUi(false, mPreviousNavigationType);
        }
    }

    private void startLatencyTracking() {
        if (mTrackingLatency) {
            cancelLatencyTracking();
        }
        mLatencyTracker.onActionStart(LatencyTracker.ACTION_BACK_SYSTEM_ANIMATION);
        mTrackingLatency = true;
    }

    private void cancelLatencyTracking() {
        if (!mTrackingLatency) {
            return;
        }
        mLatencyTracker.onActionCancel(LatencyTracker.ACTION_BACK_SYSTEM_ANIMATION);
        mTrackingLatency = false;
    }

    private void endLatencyTracking() {
        if (!mTrackingLatency) {
            return;
        }
        mLatencyTracker.onActionEnd(LatencyTracker.ACTION_BACK_SYSTEM_ANIMATION);
        mTrackingLatency = false;
    }

    private void startSystemAnimation() {
        if (mBackNavigationInfo == null) {
            ProtoLog.e(WM_SHELL_BACK_PREVIEW, "Lack of navigation info to start animation.");
            return;
        }
        if (!validateAnimationTargets(mApps)) {
            ProtoLog.w(WM_SHELL_BACK_PREVIEW, "Not starting animation due to mApps being null.");
            return;
        }

        final BackAnimationRunner runner =
                mShellBackAnimationRegistry.getAnimationRunnerAndInit(mBackNavigationInfo);
        if (runner == null) {
            if (mBackAnimationFinishedCallback != null) {
                try {
                    mBackAnimationFinishedCallback.onAnimationFinished(false);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed call IBackNaviAnimationController", e);
                }
            }
            return;
        }
        mActiveCallback = runner.getCallback();

        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "BackAnimationController: startAnimation()");

        runner.startAnimation(mApps, /*wallpapers*/ null, /*nonApps*/ null,
                () -> mShellExecutor.execute(this::onBackAnimationFinished));

        if (mApps.length >= 1) {
            BackMotionEvent startEvent = mCurrentTracker.createStartEvent(
                    Flags.removeDepartTargetFromMotion() ? null : mApps[0]);
            dispatchOnBackStarted(mActiveCallback, startEvent);
            if (startEvent.getSwipeEdge() == EDGE_NONE) {
                // TODO(b/373544911): onBackStarted is dispatched here so that
                //  WindowOnBackInvokedDispatcher knows about the back navigation and intercepts
                //  touch events while it's active. It would be cleaner and safer to disable
                //  multitouch altogether (same as in gesture-nav).
                dispatchOnBackStarted(mBackNavigationInfo.getOnBackInvokedCallback(), startEvent);
            }
        }
    }

    private void requestTopUi(boolean hasTopUi, int backType) {
        if (mRequestTopUiCallback != null && (backType == BackNavigationInfo.TYPE_CROSS_TASK
                || backType == BackNavigationInfo.TYPE_CROSS_ACTIVITY)) {
            mRequestTopUiCallback.requestTopUi(hasTopUi, TAG);
        }
    }

    /**
     * Validate animation targets.
     */
    static boolean validateAnimationTargets(RemoteAnimationTarget[] apps) {
        if (apps == null || apps.length == 0) {
            return false;
        }
        for (int i = apps.length - 1; i >= 0; --i) {
            if (!apps[i].leash.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void kickStartAnimation() {
        startSystemAnimation();

        // Dispatch the first progress after animation start for
        // smoothing the initial animation, instead of waiting for next
        // onMove.
        final BackMotionEvent backFinish = mCurrentTracker
                .createProgressEvent();
        dispatchOnBackProgressed(mActiveCallback, backFinish);
        if (mCurrentTracker.isFinished()) {
            // if the down -> up gesture happened before animation
            // start, we have to trigger the uninterruptible transition
            // to finish the back animation.
            startPostCommitAnimation();
        }
    }

    private void createAdapter() {
        IBackAnimationRunner runner =
                new IBackAnimationRunner.Stub() {
                    @Override
                    public void onAnimationStart(
                            RemoteAnimationTarget[] apps,
                            IBinder token,
                            IBackAnimationFinishedCallback finishedCallback) {
                        mShellExecutor.execute(
                                () -> {
                                    endLatencyTracking();
                                    if (!validateAnimationTargets(apps)) {
                                        Log.e(TAG, "Invalid animation targets!");
                                        return;
                                    }
                                    mBackAnimationFinishedCallback = finishedCallback;
                                    mApps = apps;
                                    // app only visible after transition ready, break for now.
                                    if (token != null) {
                                        return;
                                    }
                                    kickStartAnimation();
                                });
                    }

                    @Override
                    public void onAnimationCancelled() {
                        mShellExecutor.execute(
                                () -> {
                                    if (!mShellBackAnimationRegistry.cancel(
                                            mBackNavigationInfo != null
                                                    ? mBackNavigationInfo.getType()
                                                    : mPreviousNavigationType)) {
                                        return;
                                    }
                                    if (!mBackGestureStarted) {
                                        invokeOrCancelBack(mCurrentTracker);
                                    }
                                });
                    }
                };
        mBackAnimationAdapter = new BackAnimationAdapter(runner);
    }

    private void registerBackGestureDelegate() {
        mInputManager.registerKeyGestureEventHandler(List.of(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
                (event, focussedToken) -> {
                    if (event.getKeyGestureType() == KeyGestureEvent.KEY_GESTURE_TYPE_BACK) {
                        mShellExecutor.execute(() -> {
                            if (mBackGestureStarted) {
                                Log.w(TAG, "Back gesture is running, ignore request");
                                return;
                            }
                            onMotionEvent(0, 0, KeyEvent.ACTION_DOWN, EDGE_NONE, INVALID_DISPLAY);
                            setTriggerBack(true);
                            onMotionEvent(0, 0, KeyEvent.ACTION_UP, EDGE_NONE, INVALID_DISPLAY);
                        });
                    } else {
                        Log.w(TAG, "Unsupported gesture " + event + " received!");
                    }
                });
    }

    /**
     * Description of current BackAnimationController state.
     */
    private void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "BackAnimationController state:");
        pw.println(prefix + "  mBackGestureStarted=" + mBackGestureStarted);
        pw.println(prefix + "  mPostCommitAnimationInProgress=" + mPostCommitAnimationInProgress);
        pw.println(prefix + "  mShouldStartOnNextMoveEvent=" + mShouldStartOnNextMoveEvent);
        pw.println(prefix + "  mPointerPilfered=" + mThresholdCrossed);
        pw.println(prefix + "  mRequirePointerPilfer=" + mRequirePointerPilfer);
        pw.println(prefix + "  mCurrentTracker state:");
        mCurrentTracker.dump(pw, prefix + "    ");
        pw.println(prefix + "  mQueuedTracker state:");
        mQueuedTracker.dump(pw, prefix + "    ");
    }

    class BackTransitionHandler implements Transitions.TransitionHandler {
        private final Transitions mTransitions;

        Runnable mOnAnimationFinishCallback;
        boolean mCloseTransitionRequested;
        SurfaceControl.Transaction mFinishOpenTransaction;
        Transitions.TransitionFinishCallback mFinishOpenTransitionCallback;
        // The Transition to make behindActivity become visible
        IBinder mPrepareOpenTransition;
        // The Transition to make behindActivity become invisible, if prepare open exist and
        // animation is canceled, start a close prepare transition to finish the whole transition.
        IBinder mClosePrepareTransition;
        TransitionInfo mOpenTransitionInfo;
        Transitions.TransitionHandler mTakeoverHandler;

        BackTransitionHandler(Transitions transitions) {
            mTransitions = transitions;
        }

        void onAnimationFinished() {
            if (!mCloseTransitionRequested && mPrepareOpenTransition != null) {
                createClosePrepareTransition();
            }
            if (mOnAnimationFinishCallback != null) {
                mOnAnimationFinishCallback.run();
                mOnAnimationFinishCallback = null;
            }
        }

        private void applyFinishOpenTransition() {
            if (mFinishOpenTransaction != null) {
                final SurfaceControl.Transaction t = mFinishOpenTransaction;
                t.apply();
            }
            if (mFinishOpenTransitionCallback != null) {
                final Transitions.TransitionFinishCallback callback = mFinishOpenTransitionCallback;
                callback.onTransitionFinished(null);
            }
            cleanUpInternalState();
        }

        private void cleanUpInternalState() {
            mOpenTransitionInfo = null;
            mPrepareOpenTransition = null;
            mFinishOpenTransaction = null;
            mFinishOpenTransitionCallback = null;
            mTakeoverHandler = null;
        }

        private void applyAndFinish(@NonNull SurfaceControl.Transaction st,
                @NonNull SurfaceControl.Transaction ft,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            applyFinishOpenTransition();
            st.apply();
            ft.apply();
            finishCallback.onTransitionFinished(null);
            mCloseTransitionRequested = false;
        }

        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction st,
                @NonNull SurfaceControl.Transaction ft,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            // Both mShellExecutor and Transitions#mMainExecutor are ShellMainThread, so we don't
            // need to post to ShellExecutor when called.
            if (info.getType() == WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION) {
                // only consume it if this transition hasn't being processed.
                if (mClosePrepareTransition != null) {
                    mClosePrepareTransition = null;
                    applyAndFinish(st, ft, finishCallback);
                    return true;
                }
                return false;
            }

            if (info.getType() != WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION
                    && isNotGestureBackTransition(info)) {
                return false;
            }

            if (shouldCancelAnimation(info)) {
                mPrepareOpenTransition = null;
                return false;
            }

            if (mApps == null || mApps.length == 0) {
                if (mCloseTransitionRequested) {
                    // animation never start, consume directly
                    applyAndFinish(st, ft, finishCallback);
                    return true;
                } else if (mClosePrepareTransition == null
                        && info.getType() == WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION) {
                    // Gesture animation was cancelled before prepare transition ready, create
                    // the close prepare transition
                    createClosePrepareTransition();
                }
            }

            if (handlePrepareTransition(transition, info, st, ft, finishCallback)) {
                if (checkTakeoverFlags()) {
                    mTakeoverHandler = mTransitions.getHandlerForTakeover(transition, info);
                }
                kickStartAnimation();
                return true;
            }
            return handleCloseTransition(info, st, ft, finishCallback);
        }

        private boolean canHandOffAnimation() {
            if (!checkTakeoverFlags()) {
                return false;
            }

            return mTakeoverHandler != null;
        }

        private void handOffAnimation(
                RemoteAnimationTarget[] targets, WindowAnimationState[] states) {
            if (!checkTakeoverFlags()) {
                ProtoLog.e(WM_SHELL_BACK_PREVIEW,
                        "Trying to hand off the animation, but the required flags are disabled.");
                return;
            } else if (mTakeoverHandler == null) {
                ProtoLog.e(WM_SHELL_BACK_PREVIEW,
                        "Missing takeover handler when trying to hand off animation.");
                return;
            } else if (targets.length != states.length) {
                ProtoLog.e(WM_SHELL_BACK_PREVIEW,
                        "Targets passed for takeover don't match the window states.");
                return;
            }

            // The states passed to this method are paired with the targets, but they need to be
            // paired with the changes inside the TransitionInfo. So for each change we find its
            // matching target, and leave the state for any change missing a matching target blank.
            WindowAnimationState[] updatedStates =
                    new WindowAnimationState[mOpenTransitionInfo.getChanges().size()];
            for (int i = 0; i < mOpenTransitionInfo.getChanges().size(); i++) {
                ActivityManager.RunningTaskInfo taskInfo =
                        mOpenTransitionInfo.getChanges().get(i).getTaskInfo();
                if (taskInfo == null) {
                    continue;
                }

                for (int j = 0; j < targets.length; j++) {
                    if (taskInfo.taskId == targets[j].taskId) {
                        updatedStates[i] = states[j];
                        break;
                    }
                }
            }

            mTakeoverHandler.takeOverAnimation(
                    mPrepareOpenTransition, mOpenTransitionInfo, new SurfaceControl.Transaction(),
                    mFinishOpenTransitionCallback, updatedStates);

            cleanUpInternalState();
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @Nullable SurfaceControl.Transaction finishTransaction) {
            if (transition == mClosePrepareTransition && aborted) {
                mClosePrepareTransition = null;
                applyFinishOpenTransition();
            } else if (!aborted && unifyBackNavigationTransition()) {
                // Since the closing target participates in the predictive back transition, the
                // merged transition must be applied with the first transition to ensure a seamless
                // animation.
                if (mFinishOpenTransaction != null && finishTransaction != null) {
                    mFinishOpenTransaction.merge(finishTransaction);
                }
            }
        }

        void createClosePrepareTransition() {
            if (mClosePrepareTransition != null) {
                Log.e(TAG, "Re-create close prepare transition");
                return;
            }
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.restoreBackNavi();
            mClosePrepareTransition = mTransitions.startTransition(
                    TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION, wct, mBackTransitionHandler);
        }
        private void mergePendingTransitions(TransitionInfo info) {
            if (mOpenTransitionInfo == null) {
                return;
            }
            // Copy initial changes to final transition
            final TransitionInfo init = mOpenTransitionInfo;
            // Find prepare open target
            boolean openShowWallpaper = false;
            final ArrayList<SurfaceControl> openSurfaces = new ArrayList<>();
            int tmpSize;
            for (int j = init.getChanges().size() - 1; j >= 0; --j) {
                final TransitionInfo.Change change = init.getChanges().get(j);
                if (change.hasFlags(FLAG_BACK_GESTURE_ANIMATED)
                        && TransitionUtil.isOpeningMode(change.getMode())) {
                    final ComponentName openComponent = findComponentName(change);
                    final int openTaskId = findTaskId(change);
                    final WindowContainerToken openToken = findToken(change);
                    if (openComponent == null && openTaskId == INVALID_TASK_ID
                            && openToken == null) {
                        continue;
                    }
                    openSurfaces.add(change.getLeash());
                    if (change.hasFlags(FLAG_SHOW_WALLPAPER)) {
                        openShowWallpaper = true;
                    }
                }
            }
            if (openSurfaces.isEmpty()) {
                // This shouldn't happen, but if that happen, consume the initial transition anyway.
                Log.e(TAG, "Unable to merge following transition, cannot find the gesture "
                        + "animated target from the open transition=" + mOpenTransitionInfo);
                mOpenTransitionInfo = null;
                return;
            }
            // Find first non-prepare open target
            boolean isOpen = false;
            tmpSize = info.getChanges().size();
            for (int j = 0; j < tmpSize; ++j) {
                final TransitionInfo.Change change = info.getChanges().get(j);
                if (isOpenSurfaceMatched(openSurfaces, change)) {
                    // This is original close target, potential be close, but cannot determine
                    // from it.
                    if (change.hasFlags(FLAG_BACK_GESTURE_ANIMATED)) {
                        isOpen = !TransitionUtil.isClosingMode(change.getMode());
                    } else {
                        isOpen = TransitionUtil.isOpeningMode(change.getMode());
                        break;
                    }
                }
            }
            if (!isOpen) {
                // Close transition, the transition info should be:
                // init info(open A & wallpaper) => init info(open A & change B & wallpaper)
                // current info(close B target) => current info(change A & close B)
                // remove init info(open/change A target & wallpaper)
                boolean moveToTop = false;
                boolean excludeOpenTarget = false;
                boolean mergePredictive = false;
                for (int j = info.getChanges().size() - 1; j >= 0; --j) {
                    final TransitionInfo.Change change = info.getChanges().get(j);
                    if (isOpenSurfaceMatched(openSurfaces, change)) {
                        if (TransitionUtil.isClosingMode(change.getMode())) {
                            excludeOpenTarget = true;
                        }
                        moveToTop = change.hasFlags(FLAG_MOVED_TO_TOP);
                        info.getChanges().remove(j);
                    } else if ((openShowWallpaper && change.hasFlags(FLAG_IS_WALLPAPER))) {
                        info.getChanges().remove(j);
                    } else if (!mergePredictive && TransitionUtil.isClosingMode(change.getMode())) {
                        mergePredictive = true;
                    }
                }
                // Ignore merge if there is no close target
                if (!info.getChanges().isEmpty() && mergePredictive) {
                    tmpSize = init.getChanges().size();
                    for (int i = 0; i < tmpSize; ++i) {
                        final TransitionInfo.Change change = init.getChanges().get(i);
                        if (change.hasFlags(FLAG_IS_WALLPAPER)) {
                            continue;
                        }
                        if (isOpenSurfaceMatched(openSurfaces, change)) {
                            if (excludeOpenTarget) {
                                // App has triggered another change during predictive back
                                // transition, filter out predictive back target.
                                continue;
                            }
                            if (moveToTop) {
                                change.setFlags(change.getFlags() | FLAG_MOVED_TO_TOP);
                            }
                        } else if (Flags.unifyBackNavigationTransition()
                                && change.hasFlags(FLAG_BACK_GESTURE_ANIMATED)
                                && change.getMode() == TRANSIT_CHANGE
                                && isCloseChangeExist(info, change)) {
                            // This is the original top target, don't add it into current transition
                            // if it is closing.
                            continue;
                        }
                        info.getChanges().add(i, change);
                    }
                }
            } else {
                // Open transition, the transition info should be:
                // init info(open A & wallpaper)
                // current info(open C target + close B target + close A & wallpaper)

                // If close target isn't back navigated, filter out close A & wallpaper because the
                // (open C + close B) pair didn't participant prepare close
                boolean nonBackOpen = false;
                boolean nonBackClose = false;
                tmpSize = info.getChanges().size();
                for (int j = 0; j < tmpSize; ++j) {
                    final TransitionInfo.Change change = info.getChanges().get(j);
                    if (!change.hasFlags(FLAG_BACK_GESTURE_ANIMATED)
                            && canBeTransitionTarget(change)) {
                        final int mode = change.getMode();
                        nonBackOpen |= TransitionUtil.isOpeningMode(mode);
                        nonBackClose |= TransitionUtil.isClosingMode(mode);
                    }
                }
                if (nonBackClose && nonBackOpen) {
                    for (int j = info.getChanges().size() - 1; j >= 0; --j) {
                        final TransitionInfo.Change change = info.getChanges().get(j);
                        if (isOpenSurfaceMatched(openSurfaces, change)) {
                            info.getChanges().remove(j);
                        } else if ((openShowWallpaper && change.hasFlags(FLAG_IS_WALLPAPER))) {
                            info.getChanges().remove(j);
                        }
                    }
                }
            }
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Back animation transition, merge pending "
                    + "transitions result=%s", info);
            // Only handle one merge transition request.
            mOpenTransitionInfo = null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mClosePrepareTransition == transition) {
                mClosePrepareTransition = null;
            }
            // try to handle unexpected transition
            if (mOpenTransitionInfo != null) {
                mergePendingTransitions(info);
            }

            if (info.getType() == TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION
                    && !mCloseTransitionRequested && info.getChanges().isEmpty() && mApps == null) {
                finishCallback.onTransitionFinished(null);
                startT.apply();
                applyFinishOpenTransition();
                return;
            }
            if (isNotGestureBackTransition(info) || shouldCancelAnimation(info)
                    || !mCloseTransitionRequested) {
                if (mPrepareOpenTransition != null) {
                    applyFinishOpenTransition();
                }
                return;
            }
            // Handle the commit transition if this handler is running the open transition.
            finishCallback.onTransitionFinished(null);
            startT.apply();
            if (mCloseTransitionRequested) {
                if (mApps == null || mApps.length == 0) {
                    // animation was done
                    applyFinishOpenTransition();
                    mCloseTransitionRequested = false;
                } else {
                    // we are animating, wait until animation finish
                    mOnAnimationFinishCallback = () -> {
                        applyFinishOpenTransition();
                        mCloseTransitionRequested = false;
                    };
                }
            }
        }

        // Cancel close animation if something happen unexpected, let another handler to handle
        private boolean shouldCancelAnimation(@NonNull TransitionInfo info) {
            final boolean noCloseAllowed = !mCloseTransitionRequested
                    && info.getType() == WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
            boolean unableToHandle = false;
            boolean filterTargets = false;
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change c = info.getChanges().get(i);
                final boolean backGestureAnimated = c.hasFlags(FLAG_BACK_GESTURE_ANIMATED);
                if (!backGestureAnimated && !c.hasFlags(FLAG_IS_WALLPAPER)) {
                    // something we cannot handle?
                    unableToHandle = true;
                    filterTargets = true;
                } else if (noCloseAllowed && backGestureAnimated
                        && TransitionUtil.isClosingMode(c.getMode())) {
                    // Prepare back navigation shouldn't contain close change, unless top app
                    // request close.
                    unableToHandle = true;
                }
            }
            if (!unableToHandle) {
                return false;
            }
            if (!filterTargets) {
                return true;
            }
            if (TransitionUtil.isOpeningType(info.getType())
                    || TransitionUtil.isClosingType(info.getType())) {
                boolean removeWallpaper = false;
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    final TransitionInfo.Change c = info.getChanges().get(i);
                    // filter out opening target, keep original closing target in this transition
                    if (c.hasFlags(FLAG_BACK_GESTURE_ANIMATED)
                            && TransitionUtil.isOpeningMode(c.getMode())) {
                        info.getChanges().remove(i);
                        removeWallpaper |= c.hasFlags(FLAG_SHOW_WALLPAPER);
                    }
                }
                if (removeWallpaper) {
                    for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                        final TransitionInfo.Change c = info.getChanges().get(i);
                        if (c.hasFlags(FLAG_IS_WALLPAPER)) {
                            info.getChanges().remove(i);
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Check whether this transition is prepare for predictive back animation, which could
         * happen when core make an activity become visible.
         */
        @VisibleForTesting
        boolean handlePrepareTransition(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction st,
                @NonNull SurfaceControl.Transaction ft,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (info.getType() != WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION) {
                return false;
            }
            // Must have open target, must not have close target.
            if (hasAnimationInMode(info, TransitionUtil::isClosingMode)
                    || !hasAnimationInMode(info, TransitionUtil::isOpeningMode)) {
                return false;
            }
            SurfaceControl openingLeash = null;
            SurfaceControl closingLeash = null;
            if (mApps != null) {
                for (int i = mApps.length - 1; i >= 0; --i) {
                    if (mApps[i].mode == MODE_OPENING) {
                        openingLeash = mApps[i].leash;
                    } else if (mApps[i].mode == MODE_CLOSING) {
                        closingLeash = mApps[i].leash;
                    }
                }
            }
            if (openingLeash != null && closingLeash != null) {
                int rootIdx = -1;
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    final TransitionInfo.Change c = info.getChanges().get(i);
                    if (TransitionUtil.isOpeningMode(c.getMode())) {
                        final Point offset = c.getEndRelOffset();
                        st.setPosition(c.getLeash(), offset.x, offset.y);
                        st.reparent(c.getLeash(), openingLeash);
                        st.setAlpha(c.getLeash(), 1.0f);
                        rootIdx = TransitionUtil.rootIndexFor(c, info);
                    } else if (c.hasFlags(FLAG_BACK_GESTURE_ANIMATED)
                            && c.getMode() == TRANSIT_CHANGE) {
                        st.reparent(c.getLeash(), closingLeash);
                    }
                }
                // The root leash and the leash of opening target should actually in the same level,
                // but since the root leash is created after opening target, it will have higher
                // layer in surface flinger. Move the root leash to lower level, so it won't affect
                // the playing animation.
                if (rootIdx >= 0 && info.getRootCount() > 0) {
                    st.setLayer(info.getRoot(rootIdx).getLeash(), -1);
                }
            }
            st.apply();
            // In case other transition handler took the handleRequest before this class.
            mPrepareOpenTransition = transition;
            mFinishOpenTransaction = ft;
            mFinishOpenTransitionCallback = finishCallback;
            mOpenTransitionInfo = info;
            return true;
        }

        /**
         * Check whether this transition is triggered from back gesture commitment.
         * Reparent the transition targets to animation leashes, so the animation won't be broken.
         */
        @VisibleForTesting
        boolean handleCloseTransition(@NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction st,
                @NonNull SurfaceControl.Transaction ft,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (!mCloseTransitionRequested) {
                return false;
            }
            // must have close target
            if (!hasAnimationInMode(info, TransitionUtil::isClosingMode)) {
                return false;
            }
            if (mApps == null) {
                // animation is done
                applyAndFinish(st, ft, finishCallback);
                return true;
            }
            SurfaceControl openingLeash = null;
            SurfaceControl closingLeash = null;
            for (int i = mApps.length - 1; i >= 0; --i) {
                if (mApps[i].mode == MODE_OPENING) {
                    openingLeash = mApps[i].leash;
                }
                if (mApps[i].mode == MODE_CLOSING) {
                    closingLeash = mApps[i].leash;
                }
            }
            if (openingLeash != null && closingLeash != null) {
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    final TransitionInfo.Change c = info.getChanges().get(i);
                    if (c.hasFlags(FLAG_IS_WALLPAPER)) {
                        st.setAlpha(c.getLeash(), 1.0f);
                        continue;
                    }
                    if (TransitionUtil.isOpeningMode(c.getMode())) {
                        final Point offset = c.getEndRelOffset();
                        st.setPosition(c.getLeash(), offset.x, offset.y);
                        st.reparent(c.getLeash(), openingLeash);
                        st.setAlpha(c.getLeash(), 1.0f);
                    } else if (TransitionUtil.isClosingMode(c.getMode())) {
                        st.reparent(c.getLeash(), closingLeash);
                    }
                }
            }
            st.apply();
            // mApps must exists
            mOnAnimationFinishCallback = () -> {
                ft.apply();
                finishCallback.onTransitionFinished(null);
                mCloseTransitionRequested = false;
            };
            return true;
        }

        @Nullable
        @Override
        public WindowContainerTransaction handleRequest(
                @NonNull IBinder transition,
                @NonNull TransitionRequestInfo request) {
            final int type = request.getType();
            if (type == WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION) {
                mPrepareOpenTransition = transition;
                return new WindowContainerTransaction();
            }
            if (type == WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION) {
                return new WindowContainerTransaction();
            }
            if (TransitionUtil.isClosingType(request.getType()) && mCloseTransitionRequested) {
                return new WindowContainerTransaction();
            }
            return null;
        }

        private static boolean checkTakeoverFlags() {
            return TransitionAnimator.Companion.longLivedReturnAnimationsEnabled()
                    && Flags.unifyBackNavigationTransition();
        }
    }

    private static boolean isNotGestureBackTransition(@NonNull TransitionInfo info) {
        return !hasAnimationInMode(info, TransitionUtil::isOpenOrCloseMode);
    }

    private static boolean hasAnimationInMode(@NonNull TransitionInfo info,
            Predicate<Integer> mode) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change c = info.getChanges().get(i);
            if (c.hasFlags(FLAG_BACK_GESTURE_ANIMATED) && mode.test(c.getMode())) {
                return true;
            }
        }
        return false;
    }

    private static WindowContainerToken findToken(TransitionInfo.Change change) {
        return change.getContainer();
    }

    private static ComponentName findComponentName(TransitionInfo.Change change) {
        final ComponentName componentName = change.getActivityComponent();
        if (componentName != null) {
            return componentName;
        }
        final TaskInfo taskInfo = change.getTaskInfo();
        if (taskInfo != null) {
            return taskInfo.topActivity;
        }
        return null;
    }

    private static int findTaskId(TransitionInfo.Change change) {
        final TaskInfo taskInfo = change.getTaskInfo();
        if (taskInfo != null) {
            return taskInfo.taskId;
        }
        return INVALID_TASK_ID;
    }

    static boolean isOpenSurfaceMatched(@NonNull ArrayList<SurfaceControl> openSurfaces,
            TransitionInfo.Change change) {
        for (int i = openSurfaces.size() - 1; i >= 0; --i) {
            if (openSurfaces.get(i).isSameSurface(change.getLeash())) {
                return true;
            }
        }
        return false;
    }

    private static boolean canBeTransitionTarget(TransitionInfo.Change change) {
        return findComponentName(change) != null || findTaskId(change) != INVALID_TASK_ID;
    }

    private static boolean isCloseChangeExist(TransitionInfo info, TransitionInfo.Change change) {
        for (int j = info.getChanges().size() - 1; j >= 0; --j) {
            final TransitionInfo.Change current = info.getChanges().get(j);
            if (TransitionUtil.isClosingMode(current.getMode())
                    && change.getLeash().isSameSurface(current.getLeash())) {
                return true;
            }
        }
        return false;
    }

    // Record the latest back gesture happen on which task.
    static class BackTransitionObserver implements Transitions.TransitionObserver {
        int mFocusedTaskId = INVALID_TASK_ID;
        IBinder mFocusTaskMonitorToken;
        private BackTransitionHandler mBackTransitionHandler;
        void setBackTransitionHandler(BackTransitionHandler handler) {
            mBackTransitionHandler = handler;
        }

        void update(int focusedTaskId) {
            mFocusedTaskId = focusedTaskId;
        }

        @Override
        public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (mFocusedTaskId == INVALID_TASK_ID) {
                return;
            }
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change c = info.getChanges().get(i);
                if (c.getTaskInfo() != null && c.getTaskInfo().taskId == mFocusedTaskId) {
                    mFocusTaskMonitorToken = transition;
                    break;
                }
            }
            // Transition happen but the task isn't involved, reset.
            if (mFocusTaskMonitorToken == null) {
                mFocusedTaskId = INVALID_TASK_ID;
            }
        }

        @Override
        public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {
            if (mFocusTaskMonitorToken == merged) {
                mFocusTaskMonitorToken = playing;
            }
            if (mBackTransitionHandler.mClosePrepareTransition == merged) {
                mBackTransitionHandler.mClosePrepareTransition = null;
            }
        }

        @Override
        public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {
            if (mFocusTaskMonitorToken == transition) {
                mFocusedTaskId = INVALID_TASK_ID;
            }
            if (mBackTransitionHandler.mClosePrepareTransition == transition) {
                mBackTransitionHandler.mClosePrepareTransition = null;
            }
        }
    }
}
