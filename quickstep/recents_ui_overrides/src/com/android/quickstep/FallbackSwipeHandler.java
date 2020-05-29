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

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;

import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;

import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

/**
 * Handles the navigation gestures when a 3rd party launcher is the default home activity.
 */
public class FallbackSwipeHandler extends
        BaseSwipeUpHandlerV2<RecentsActivity, FallbackRecentsView> {

    private FallbackHomeAnimationFactory mActiveAnimationFactory;
    private final boolean mRunningOverHome;

    private final Matrix mTmpMatrix = new Matrix();
    private float mMaxLauncherScale = 1;

    public FallbackSwipeHandler(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);

        mRunningOverHome = ActivityManagerWrapper.isHomeTask(mGestureState.getRunningTask());
        if (mRunningOverHome) {
            mTransformParams.setHomeBuilderProxy(this::updateHomeActivityTransformDuringSwipeUp);
        }
    }

    @Override
    protected void initTransitionEndpoints(DeviceProfile dp) {
        super.initTransitionEndpoints(dp);
        if (mRunningOverHome) {
            mMaxLauncherScale = 1 / mTaskViewSimulator.getFullScreenScale();
        }
    }

    private void updateHomeActivityTransformDuringSwipeUp(SurfaceParams.Builder builder,
            RemoteAnimationTargetCompat app, TransformParams params) {
        setHomeScaleAndAlpha(builder, app, mCurrentShift.value,
                Utilities.boundToRange(1 - mCurrentShift.value, 0, 1));
    }

    private void setHomeScaleAndAlpha(SurfaceParams.Builder builder,
            RemoteAnimationTargetCompat app, float verticalShift, float alpha) {
        float scale = Utilities.mapRange(verticalShift, 1, mMaxLauncherScale);
        mTmpMatrix.setScale(scale, scale,
                app.localBounds.exactCenterX(), app.localBounds.exactCenterY());
        builder.withMatrix(mTmpMatrix).withAlpha(alpha);
    }

    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(long duration) {
        mActiveAnimationFactory = new FallbackHomeAnimationFactory(duration);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        mContext.startActivity(new Intent(mGestureState.getHomeIntent()), options.toBundle());
        return mActiveAnimationFactory;
    }

    @Override
    protected boolean handleTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
        if (mActiveAnimationFactory != null
                && mActiveAnimationFactory.handleHomeTaskAppeared(appearedTaskTarget)) {
            mActiveAnimationFactory = null;
            return false;
        }

        return super.handleTaskAppeared(appearedTaskTarget);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        mRecentsAnimationController.finish(
                false /* toRecents */, callback, true /* sendUserLeaveHint */);
    }

    @Override
    protected void notifyGestureAnimationStartToRecents() {
        if (mRunningOverHome) {
            mRecentsView.onGestureAnimationStartOnHome(mGestureState.getRunningTask());
        } else {
            super.notifyGestureAnimationStartToRecents();
        }
    }

    private class FallbackHomeAnimationFactory extends HomeAnimationFactory {

        private final TransformParams mHomeAlphaParams = new TransformParams();
        private final AnimatedFloat mHomeAlpha;

        private final AnimatedFloat mVerticalShiftForScale = new AnimatedFloat();

        private final AnimatedFloat mRecentsAlpha = new AnimatedFloat();

        private final long mDuration;
        FallbackHomeAnimationFactory(long duration) {
            super(null);
            mDuration = duration;

            if (mRunningOverHome) {
                mHomeAlpha = new AnimatedFloat();
                mHomeAlpha.value = Utilities.boundToRange(1 - mCurrentShift.value, 0, 1);
                mVerticalShiftForScale.value = mCurrentShift.value;
                mTransformParams.setHomeBuilderProxy(
                        this::updateHomeActivityTransformDuringHomeAnim);
            } else {
                mHomeAlpha = new AnimatedFloat(this::updateHomeAlpha);
                mHomeAlpha.value = 0;

                mHomeAlphaParams.setHomeBuilderProxy(
                        this::updateHomeActivityTransformDuringHomeAnim);
            }

            mRecentsAlpha.value = 1;
            mTransformParams.setBaseBuilderProxy(
                    this::updateRecentsActivityTransformDuringHomeAnim);
        }

        private void updateRecentsActivityTransformDuringHomeAnim(SurfaceParams.Builder builder,
                RemoteAnimationTargetCompat app, TransformParams params) {
            builder.withAlpha(mRecentsAlpha.value);
        }

        private void updateHomeActivityTransformDuringHomeAnim(SurfaceParams.Builder builder,
                RemoteAnimationTargetCompat app, TransformParams params) {
            setHomeScaleAndAlpha(builder, app, mVerticalShiftForScale.value, mHomeAlpha.value);
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            PendingAnimation pa = new PendingAnimation(mDuration);
            pa.setFloat(mRecentsAlpha, AnimatedFloat.VALUE, 0, ACCEL);
            return pa.createPlaybackController();
        }

        private void updateHomeAlpha() {
            if (mHomeAlphaParams.getTargetSet() != null) {
                mHomeAlphaParams.applySurfaceParams(
                        mHomeAlphaParams.createSurfaceParams(BuilderProxy.NO_OP));
            }
        }

        public boolean handleHomeTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
            if (appearedTaskTarget.activityType == ACTIVITY_TYPE_HOME) {
                RemoteAnimationTargets targets = new RemoteAnimationTargets(
                        new RemoteAnimationTargetCompat[] {appearedTaskTarget},
                        new RemoteAnimationTargetCompat[0], appearedTaskTarget.mode);
                mHomeAlphaParams.setTargetSet(targets);
                updateHomeAlpha();
                return true;
            }
            return false;
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            ObjectAnimator alphaAnim = mHomeAlpha.animateToValue(mHomeAlpha.value, 1);
            alphaAnim.setDuration(mDuration).setInterpolator(ACCEL);
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
    }
}
