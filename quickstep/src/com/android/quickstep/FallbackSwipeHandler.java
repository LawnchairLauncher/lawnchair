/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_USER;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.launcher3.GestureNavContract.EXTRA_GESTURE_CONTRACT;
import static com.android.launcher3.GestureNavContract.EXTRA_ICON_POSITION;
import static com.android.launcher3.GestureNavContract.EXTRA_ICON_SURFACE;
import static com.android.launcher3.GestureNavContract.EXTRA_ON_FINISH_CALLBACK;
import static com.android.launcher3.GestureNavContract.EXTRA_REMOTE_CALLBACK;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;

import android.animation.ObjectAnimator;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.InputConsumerController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles the navigation gestures when a 3rd party launcher is the default home activity.
 */
public class FallbackSwipeHandler extends
        AbsSwipeUpHandler<RecentsActivity, FallbackRecentsView, RecentsState> {

    private static final String TAG = "FallbackSwipeHandler";

    /**
     * Message used for receiving gesture nav contract information. We use a static messenger to
     * avoid leaking too make binders in case the receiving launcher does not handle the contract
     * properly.
     */
    private static StaticMessageReceiver sMessageReceiver = null;

    private FallbackHomeAnimationFactory mActiveAnimationFactory;
    private final boolean mRunningOverHome;

    private final Matrix mTmpMatrix = new Matrix();
    private float mMaxLauncherScale = 1;

    private boolean mAppCanEnterPip;

    public FallbackSwipeHandler(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);

        mRunningOverHome = mGestureState.getRunningTask() != null
                && mGestureState.getRunningTask().isHomeTask();
        if (mRunningOverHome) {
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                    FallbackSwipeHandler.this::updateHomeActivityTransformDuringSwipeUp));
        }
    }

    @Override
    protected void initTransitionEndpoints(DeviceProfile dp) {
        super.initTransitionEndpoints(dp);
        if (mRunningOverHome) {
            // Full screen scale should be independent of remote target handle
            mMaxLauncherScale = 1 / mRemoteTargetHandles[0].getTaskViewSimulator()
                    .getFullScreenScale();
        }
    }

    private void updateHomeActivityTransformDuringSwipeUp(SurfaceProperties builder,
            RemoteAnimationTarget app, TransformParams params) {
        setHomeScaleAndAlpha(builder, app, mCurrentShift.value,
                Utilities.boundToRange(1 - mCurrentShift.value, 0, 1));
    }

    private void setHomeScaleAndAlpha(SurfaceProperties builder,
            RemoteAnimationTarget app, float verticalShift, float alpha) {
        float scale = Utilities.mapRange(verticalShift, 1, mMaxLauncherScale);
        mTmpMatrix.setScale(scale, scale,
                app.localBounds.exactCenterX(), app.localBounds.exactCenterY());
        builder.setMatrix(mTmpMatrix).setAlpha(alpha);
        builder.setShow();
    }

    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(ArrayList<IBinder> launchCookies,
            long duration, boolean isTargetTranslucent, boolean appCanEnterPip,
            RemoteAnimationTarget runningTaskTarget) {
        mAppCanEnterPip = appCanEnterPip;
        if (appCanEnterPip) {
            return new FallbackPipToHomeAnimationFactory();
        }
        mActiveAnimationFactory = new FallbackHomeAnimationFactory(duration);
        startHomeIntent(mActiveAnimationFactory, runningTaskTarget, "FallbackSwipeHandler-home");
        return mActiveAnimationFactory;
    }

    private void startHomeIntent(
            @Nullable FallbackHomeAnimationFactory gestureContractAnimationFactory,
            @Nullable RemoteAnimationTarget runningTaskTarget,
            @NonNull String reason) {
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        Intent intent = new Intent(mGestureState.getHomeIntent());
        if (gestureContractAnimationFactory != null && runningTaskTarget != null) {
            gestureContractAnimationFactory.addGestureContract(intent, runningTaskTarget.taskInfo);
        }
        startHomeIntentSafely(mContext, intent, options.toBundle(), reason);
    }

    @Override
    protected boolean handleTaskAppeared(RemoteAnimationTarget[] appearedTaskTarget) {
        if (mActiveAnimationFactory != null
                && mActiveAnimationFactory.handleHomeTaskAppeared(appearedTaskTarget)) {
            mActiveAnimationFactory = null;
            return false;
        }

        return super.handleTaskAppeared(appearedTaskTarget);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        final Runnable recentsCallback;
        if (mAppCanEnterPip) {
            // Make sure Launcher is resumed after auto-enter-pip transition to actually trigger
            // the PiP task appearing.
            recentsCallback = () -> {
                callback.run();
                startHomeIntent(null /* gestureContractAnimationFactory */,
                        null /* runningTaskTarget */, "FallbackSwipeHandler-resumeLauncher");
            };
        } else {
            recentsCallback = callback;
        }
        mRecentsView.cleanupRemoteTargets();
        mRecentsAnimationController.finish(
                mAppCanEnterPip /* toRecents */, recentsCallback, true /* sendUserLeaveHint */);
    }

    @Override
    protected void switchToScreenshot() {
        if (mRunningOverHome) {
            // When the current task is home, then we don't need to capture anything
            mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            super.switchToScreenshot();
        }
    }

    @Override
    protected void notifyGestureAnimationStartToRecents() {
        if (mRunningOverHome) {
            if (DisplayController.getNavigationMode(mContext).hasGestures) {
                mRecentsView.onGestureAnimationStartOnHome(
                        mGestureState.getRunningTask().getPlaceholderTasks(),
                        mDeviceState.getRotationTouchHelper());
            }
        } else {
            super.notifyGestureAnimationStartToRecents();
        }
    }

    private class FallbackPipToHomeAnimationFactory extends HomeAnimationFactory {
        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            // copied from {@link LauncherSwipeHandlerV2.LauncherHomeAnimationFactory}
            long accuracy = 2 * Math.max(mDp.widthPx, mDp.heightPx);
            return mContainer.getStateManager().createAnimationToNewWorkspace(
                    RecentsState.HOME, accuracy, StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }
    }

    private class FallbackHomeAnimationFactory extends HomeAnimationFactory
            implements Consumer<Message> {
        private final Rect mTempRect = new Rect();
        private final TransformParams mHomeAlphaParams = new TransformParams();
        private final AnimatedFloat mHomeAlpha;

        private final AnimatedFloat mVerticalShiftForScale = new AnimatedFloat();
        private final AnimatedFloat mRecentsAlpha = new AnimatedFloat();

        private final RectF mTargetRect = new RectF();
        private SurfaceControl mSurfaceControl;

        private boolean mAnimationFinished;
        private Message mOnFinishCallback;

        private final long mDuration;

        private RectFSpringAnim mSpringAnim;
        FallbackHomeAnimationFactory(long duration) {
            mDuration = duration;

            if (mRunningOverHome) {
                mHomeAlpha = new AnimatedFloat();
                mHomeAlpha.value = Utilities.boundToRange(1 - mCurrentShift.value, 0, 1);
                mVerticalShiftForScale.value = mCurrentShift.value;
                runActionOnRemoteHandles(remoteTargetHandle ->
                        remoteTargetHandle.getTransformParams().setHomeBuilderProxy(
                                FallbackHomeAnimationFactory.this
                                        ::updateHomeActivityTransformDuringHomeAnim));
            } else {
                mHomeAlpha = new AnimatedFloat(this::updateHomeAlpha);
                mHomeAlpha.value = 0;
                mHomeAlphaParams.setHomeBuilderProxy(
                        this::updateHomeActivityTransformDuringHomeAnim);
            }

            mRecentsAlpha.value = 1;
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTransformParams().setBaseBuilderProxy(
                            FallbackHomeAnimationFactory.this
                                    ::updateRecentsActivityTransformDuringHomeAnim));
        }

        @NonNull
        @Override
        public RectF getWindowTargetRect() {
            if (mTargetRect.isEmpty()) {
                mTargetRect.set(super.getWindowTargetRect());
            }
            return mTargetRect;
        }

        private void updateRecentsActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            builder.setAlpha(mRecentsAlpha.value);
        }

        private void updateHomeActivityTransformDuringHomeAnim(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params) {
            setHomeScaleAndAlpha(builder, app, mVerticalShiftForScale.value, mHomeAlpha.value);
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            PendingAnimation pa = new PendingAnimation(mDuration);
            pa.setFloat(mRecentsAlpha, AnimatedFloat.VALUE, 0, ACCELERATE);
            return pa.createPlaybackController();
        }

        private void updateHomeAlpha() {
            if (mHomeAlphaParams.getTargetSet() != null) {
                mHomeAlphaParams.applySurfaceParams(
                        mHomeAlphaParams.createSurfaceParams(BuilderProxy.NO_OP));
            }
        }

        public boolean handleHomeTaskAppeared(RemoteAnimationTarget[] appearedTaskTargets) {
            RemoteAnimationTarget appearedTaskTarget = appearedTaskTargets[0];
            if (appearedTaskTarget.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME) {
                RemoteAnimationTargets targets = new RemoteAnimationTargets(
                        new RemoteAnimationTarget[] {appearedTaskTarget},
                        new RemoteAnimationTarget[0], new RemoteAnimationTarget[0],
                        appearedTaskTarget.mode);
                mHomeAlphaParams.setTargetSet(targets);
                updateHomeAlpha();
                return true;
            }
            return false;
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            ObjectAnimator alphaAnim = mHomeAlpha.animateToValue(mHomeAlpha.value, 1);
            alphaAnim.setDuration(mDuration).setInterpolator(ACCELERATE);
            alphaAnim.start();

            if (mRunningOverHome) {
                // Spring back launcher scale
                new SpringAnimationBuilder(mContext)
                        .setStartValue(mVerticalShiftForScale.value)
                        .setEndValue(0)
                        .setStartVelocity(-velocity / mTransitionDragLength)
                        .setMinimumVisibleChange(1f / mDp.heightPx)
                        .setDampingRatio(0.6f)
                        .setStiffness(800)
                        .build(mVerticalShiftForScale, AnimatedFloat.VALUE)
                        .start();
            }
        }

        @Override
        public void setAnimation(RectFSpringAnim anim) {
            mSpringAnim = anim;
            mSpringAnim.addAnimatorListener(forEndCallback(this::onRectAnimationEnd));
        }

        private void onRectAnimationEnd() {
            mAnimationFinished = true;
            maybeSendEndMessage();
        }

        private void maybeSendEndMessage() {
            if (mAnimationFinished && mOnFinishCallback != null) {
                try {
                    mOnFinishCallback.replyTo.send(mOnFinishCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error sending icon position", e);
                }
            }
        }

        @Override
        public void accept(Message msg) {
            try {
                Bundle data = msg.getData();
                RectF position = data.getParcelable(EXTRA_ICON_POSITION);
                if (!position.isEmpty()) {
                    mSurfaceControl = data.getParcelable(EXTRA_ICON_SURFACE);
                    mTargetRect.set(position);
                    if (mSpringAnim != null) {
                        mSpringAnim.onTargetPositionChanged();
                    }
                }
                mOnFinishCallback = data.getParcelable(EXTRA_ON_FINISH_CALLBACK);
                maybeSendEndMessage();
            } catch (Exception e) {
                // Ignore
            }
        }

        @Override
        public void update(RectF currentRect, float progress, float radius) {
            if (mSurfaceControl != null) {
                currentRect.roundOut(mTempRect);
                Transaction t = new Transaction();
                try {
                    t.setGeometry(mSurfaceControl, null, mTempRect, Surface.ROTATION_0);
                    t.apply();
                } catch (RuntimeException e) {
                    // Ignore
                }
            }
        }

        private void addGestureContract(Intent intent, RunningTaskInfo runningTaskInfo) {
            if (mRunningOverHome || runningTaskInfo == null) {
                return;
            }

            TaskKey key = new TaskKey(runningTaskInfo);
            if (key.getComponent() != null) {
                if (sMessageReceiver == null) {
                    sMessageReceiver = new StaticMessageReceiver();
                }

                Bundle gestureNavContract = new Bundle();
                gestureNavContract.putParcelable(EXTRA_COMPONENT_NAME, key.getComponent());
                gestureNavContract.putParcelable(EXTRA_USER, UserHandle.of(key.userId));
                gestureNavContract.putParcelable(
                        EXTRA_REMOTE_CALLBACK, sMessageReceiver.newCallback(this));
                intent.putExtra(EXTRA_GESTURE_CONTRACT, gestureNavContract);
            }
        }
    }

    private static class StaticMessageReceiver implements Handler.Callback {

        private final Messenger mMessenger =
                new Messenger(new Handler(Looper.getMainLooper(), this));

        private ParcelUuid mCurrentUID = new ParcelUuid(UUID.randomUUID());
        private WeakReference<Consumer<Message>> mCurrentCallback = new WeakReference<>(null);

        public Message newCallback(Consumer<Message> callback) {
            mCurrentUID = new ParcelUuid(UUID.randomUUID());
            mCurrentCallback = new WeakReference<>(callback);

            Message msg = Message.obtain();
            msg.replyTo = mMessenger;
            msg.obj = mCurrentUID;
            return msg;
        }

        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (mCurrentUID.equals(message.obj)) {
                Consumer<Message> consumer = mCurrentCallback.get();
                if (consumer != null) {
                    consumer.accept(message);
                    return true;
                }
            }
            return false;
        }
    }
}
