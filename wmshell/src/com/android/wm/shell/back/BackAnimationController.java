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

import static com.android.internal.jank.InteractionJankMonitor.CUJ_PREDICTIVE_BACK_HOME;
import static com.android.window.flags.Flags.predictiveBackSystemAnims;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_BACK_ANIMATION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.IRemoteAnimationRunner;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.WindowManager;
import android.window.BackAnimationAdapter;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackNavigationInfo;
import android.window.BackTouchTracker;
import android.window.IBackAnimationFinishedCallback;
import android.window.IBackAnimationRunner;
import android.window.IOnBackInvokedCallback;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.LatencyTracker;
import com.android.internal.view.AppearanceRegion;
import com.android.wm.shell.R;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controls the window animation run when a user initiates a back gesture.
 */
public class BackAnimationController implements RemoteCallable<BackAnimationController>,
        ConfigurationChangeListener {
    private static final String TAG = "ShellBackPreview";
    private static final int SETTING_VALUE_OFF = 0;
    private static final int SETTING_VALUE_ON = 1;
    public static final boolean IS_ENABLED =
            SystemProperties.getInt("persist.wm.debug.predictive_back",
                    SETTING_VALUE_ON) == SETTING_VALUE_ON;

    /** Predictive back animation developer option */
    private final AtomicBoolean mEnableAnimations = new AtomicBoolean(false);
    /**
     * Max duration to wait for an animation to finish before triggering the real back.
     */
    private static final long MAX_ANIMATION_DURATION = 2000;
    private final LatencyTracker mLatencyTracker;

    /** True when a back gesture is ongoing */
    private boolean mBackGestureStarted = false;

    /** Tracks if an uninterruptible animation is in progress */
    private boolean mPostCommitAnimationInProgress = false;

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
    private final IActivityTaskManager mActivityTaskManager;
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final ShellController mShellController;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellExecutor mShellExecutor;
    private final Handler mBgHandler;
    private final WindowManager mWindowManager;
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
    private RemoteAnimationTarget[] mApps;

    @VisibleForTesting
    final RemoteCallback mNavigationObserver = new RemoteCallback(
            new RemoteCallback.OnResultListener() {
                @Override
                public void onResult(@Nullable Bundle result) {
                    mShellExecutor.execute(() -> {
                        if (!mBackGestureStarted || mPostCommitAnimationInProgress) {
                            // If an uninterruptible animation is already in progress, we should
                            // ignore this due to it may cause focus lost. (alpha = 0)
                            return;
                        }
                        ProtoLog.i(WM_SHELL_BACK_PREVIEW, "Navigation window gone.");
                        setTriggerBack(false);
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

    public BackAnimationController(
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull @ShellMainThread ShellExecutor shellExecutor,
            @NonNull @ShellBackgroundThread Handler backgroundHandler,
            Context context,
            @NonNull BackAnimationBackground backAnimationBackground,
            ShellBackAnimationRegistry shellBackAnimationRegistry,
            ShellCommandHandler shellCommandHandler) {
        this(
                shellInit,
                shellController,
                shellExecutor,
                backgroundHandler,
                ActivityTaskManager.getService(),
                context,
                context.getContentResolver(),
                backAnimationBackground,
                shellBackAnimationRegistry,
                shellCommandHandler);
    }

    @VisibleForTesting
    BackAnimationController(
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull @ShellMainThread ShellExecutor shellExecutor,
            @NonNull @ShellBackgroundThread Handler bgHandler,
            @NonNull IActivityTaskManager activityTaskManager,
            Context context,
            ContentResolver contentResolver,
            @NonNull BackAnimationBackground backAnimationBackground,
            ShellBackAnimationRegistry shellBackAnimationRegistry,
            ShellCommandHandler shellCommandHandler) {
        mShellController = shellController;
        mShellExecutor = shellExecutor;
        mActivityTaskManager = activityTaskManager;
        mContext = context;
        mContentResolver = contentResolver;
        mRequirePointerPilfer =
                context.getResources().getBoolean(R.bool.config_backAnimationRequiresPointerPilfer);
        mBgHandler = bgHandler;
        shellInit.addInitCallback(this::onInit, this);
        mAnimationBackground = backAnimationBackground;
        mShellBackAnimationRegistry = shellBackAnimationRegistry;
        mLatencyTracker = LatencyTracker.getInstance(mContext);
        mShellCommandHandler = shellCommandHandler;
        mWindowManager = context.getSystemService(WindowManager.class);
        updateTouchableArea();
    }

    private void onInit() {
        setupAnimationDeveloperSettingsObserver(mContentResolver, mBgHandler);
        updateEnableAnimationFromFlags();
        createAdapter();
        mShellController.addExternalInterface(KEY_EXTRA_SHELL_BACK_ANIMATION,
                this::createExternalInterface, this);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mShellController.addConfigurationChangeListener(this);
    }

    private void setupAnimationDeveloperSettingsObserver(
            @NonNull ContentResolver contentResolver,
            @NonNull @ShellBackgroundThread final Handler backgroundHandler) {
        if (predictiveBackSystemAnims()) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Back animation aconfig flag is enabled, therefore "
                    + "developer settings flag is ignored and no content observer registered");
            return;
        }
        ContentObserver settingsObserver = new ContentObserver(backgroundHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateEnableAnimationFromFlags();
            }
        };
        contentResolver.registerContentObserver(
                Global.getUriFor(Global.ENABLE_BACK_ANIMATION),
                false, settingsObserver, UserHandle.USER_SYSTEM
        );
    }

    /**
     * Updates {@link BackAnimationController#mEnableAnimations} based on the current values of the
     * aconfig flag and the developer settings flag
     */
    @ShellBackgroundThread
    private void updateEnableAnimationFromFlags() {
        boolean isEnabled = predictiveBackSystemAnims() || isDeveloperSettingEnabled();
        mEnableAnimations.set(isEnabled);
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Back animation enabled=%s", isEnabled);
    }

    private boolean isDeveloperSettingEnabled() {
        return Global.getInt(mContext.getContentResolver(),
                Global.ENABLE_BACK_ANIMATION, SETTING_VALUE_OFF) == SETTING_VALUE_ON;
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
                float velocityX,
                float velocityY,
                int keyAction,
                @BackEvent.SwipeEdge int swipeEdge
        ) {
            mShellExecutor.execute(() -> onMotionEvent(
                    /* touchX = */ touchX,
                    /* touchY = */ touchY,
                    /* velocityX = */ velocityX,
                    /* velocityY = */ velocityY,
                    /* keyAction = */ keyAction,
                    /* swipeEdge = */ swipeEdge));
        }

        @Override
        public void onThresholdCrossed() {
            BackAnimationController.this.onThresholdCrossed();
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
    }

    private static class IBackAnimationImpl extends IBackAnimation.Stub
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
                                    CUJ_PREDICTIVE_BACK_HOME)));
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
            float velocityX,
            float velocityY,
            int keyAction,
            @BackEvent.SwipeEdge int swipeEdge) {

        BackTouchTracker activeTouchTracker = getActiveTracker();
        if (activeTouchTracker != null) {
            activeTouchTracker.update(touchX, touchY, velocityX, velocityY);
        }

        // two gestures are waiting to be processed at the moment, skip any further user touches
        if (mCurrentTracker.isFinished() && mQueuedTracker.isFinished()) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW,
                    "Ignoring MotionEvent because two gestures are already being queued.");
            return;
        }

        if (keyAction == MotionEvent.ACTION_DOWN) {
            if (!mBackGestureStarted) {
                mShouldStartOnNextMoveEvent = true;
            }
        } else if (keyAction == MotionEvent.ACTION_MOVE) {
            if (!mBackGestureStarted && mShouldStartOnNextMoveEvent) {
                // Let the animation initialized here to make sure the onPointerDownOutsideFocus
                // could be happened when ACTION_DOWN, it may change the current focus that we
                // would access it when startBackNavigation.
                onGestureStarted(touchX, touchY, swipeEdge);
                mShouldStartOnNextMoveEvent = false;
            }
            onMove();
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
        } else if (touchTracker == mCurrentTracker) {
            // Only start the back navigation if no other gesture is being processed. Otherwise,
            // the back navigation will fall back to legacy back event injection.
            startBackNavigation(mCurrentTracker);
        }
    }

    private void startBackNavigation(@NonNull BackTouchTracker touchTracker) {
        try {
            startLatencyTracking();
            mBackNavigationInfo = mActivityTaskManager.startBackNavigation(
                    mNavigationObserver, mEnableAnimations.get() ? mBackAnimationAdapter : null);
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
            cancelLatencyTracking();
            return;
        }
        final int backType = backNavigationInfo.getType();
        final boolean shouldDispatchToAnimator = shouldDispatchToAnimator();
        if (shouldDispatchToAnimator) {
            if (!mShellBackAnimationRegistry.startGesture(backType)) {
                mActiveCallback = null;
            }
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

    private void onMove() {
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

    private void injectBackKey() {
        ProtoLog.d(WM_SHELL_BACK_PREVIEW, "injectBackKey");
        sendBackEvent(KeyEvent.ACTION_DOWN);
        sendBackEvent(KeyEvent.ACTION_UP);
    }

    @SuppressLint("MissingPermission")
    private void sendBackEvent(int action) {
        final long when = SystemClock.uptimeMillis();
        final KeyEvent ev = new KeyEvent(when, when, action, KeyEvent.KEYCODE_BACK, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);

        ev.setDisplayId(mContext.getDisplay().getDisplayId());
        if (!mContext.getSystemService(InputManager.class)
                .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
            ProtoLog.e(WM_SHELL_BACK_PREVIEW, "Inject input event fail");
        }
    }

    private boolean shouldDispatchToAnimator() {
        return mEnableAnimations.get()
                && mBackNavigationInfo != null
                && mBackNavigationInfo.isPrepareRemoteAnimation();
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

        if (mBackNavigationInfo != null) {
            final IOnBackInvokedCallback callback = mBackNavigationInfo.getOnBackInvokedCallback();
            if (touchTracker.getTriggerBack()) {
                dispatchOnBackInvoked(callback);
            } else {
                tryDispatchOnBackCancelled(callback);
            }
        }
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
                injectBackKey();
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
            // notify gesture finished
            mBackNavigationInfo.onBackGestureFinished(true);
            dispatchOnBackInvoked(mActiveCallback);
        } else {
            tryDispatchOnBackCancelled(mActiveCallback);
        }
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
            injectBackKey();
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
        mShouldStartOnNextMoveEvent = false;
        mOnBackStartDispatched = false;
        mThresholdCrossed = false;
        mPointersPilfered = false;
        mShellBackAnimationRegistry.resetDefaultCrossActivity();
        cancelLatencyTracking();
        if (mBackNavigationInfo != null) {
            mPreviousNavigationType = mBackNavigationInfo.getType();
            mBackNavigationInfo.onBackNavigationFinished(triggerBack);
            mBackNavigationInfo = null;
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
            mCurrentTracker.updateStartLocation();
            BackMotionEvent startEvent = mCurrentTracker.createStartEvent(mApps[0]);
            dispatchOnBackStarted(mActiveCallback, startEvent);
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

    private void createAdapter() {
        IBackAnimationRunner runner =
                new IBackAnimationRunner.Stub() {
                    @Override
                    public void onAnimationStart(
                            RemoteAnimationTarget[] apps,
                            RemoteAnimationTarget[] wallpapers,
                            RemoteAnimationTarget[] nonApps,
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
                                    startSystemAnimation();

                                    // Dispatch the first progress after animation start for
                                    // smoothing the initial animation, instead of waiting for next
                                    // onMove.
                                    final BackMotionEvent backFinish = mCurrentTracker
                                            .createProgressEvent();
                                    dispatchOnBackProgressed(mActiveCallback, backFinish);
                                    if (!mBackGestureStarted) {
                                        // if the down -> up gesture happened before animation
                                        // start, we have to trigger the uninterruptible transition
                                        // to finish the back animation.
                                        startPostCommitAnimation();
                                    }
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

    /**
     * Description of current BackAnimationController state.
     */
    private void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "BackAnimationController state:");
        pw.println(prefix + "  mEnableAnimations=" + mEnableAnimations.get());
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

}
