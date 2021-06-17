/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.PROTOTYPE_APP_CLOSE;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.AppCloseConfig;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RectFSpringAnim2;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

public abstract class SwipeUpAnimationLogic {

    protected static final Rect TEMP_RECT = new Rect();

    protected DeviceProfile mDp;

    protected final Context mContext;
    protected final RecentsAnimationDeviceState mDeviceState;
    protected final GestureState mGestureState;
    protected final TaskViewSimulator mTaskViewSimulator;

    protected final TransformParams mTransformParams;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;

    protected AnimatorControllerWithResistance mWindowTransitionController;

    public SwipeUpAnimationLogic(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, TransformParams transformParams) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;
        mTaskViewSimulator = new TaskViewSimulator(context, gestureState.getActivityInterface());
        mTransformParams = transformParams;

        mTaskViewSimulator.getOrientationState().update(
                mDeviceState.getRotationTouchHelper().getCurrentActiveRotation(),
                mDeviceState.getRotationTouchHelper().getDisplayRotation());
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;

        mTaskViewSimulator.setDp(dp);
        mTransitionDragLength = mGestureState.getActivityInterface().getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT,
                mTaskViewSimulator.getOrientationState().getOrientationHandler());
        mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;

        PendingAnimation pa = new PendingAnimation(mTransitionDragLength * 2);
        mTaskViewSimulator.addAppToOverviewAnim(pa, LINEAR);
        AnimatorPlaybackController normalController = pa.createPlaybackController();
        mWindowTransitionController = AnimatorControllerWithResistance.createForRecents(
                normalController, mContext, mTaskViewSimulator.getOrientationState(),
                mDp, mTaskViewSimulator.recentsViewScale, AnimatedFloat.VALUE,
                mTaskViewSimulator.recentsViewSecondaryTranslation, AnimatedFloat.VALUE);
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        float shift;
        if (displacement > mTransitionDragLength * mDragLengthFactor && mTransitionDragLength > 0) {
            shift = mDragLengthFactor;
        } else {
            float translation = Math.max(displacement, 0);
            shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
        }

        mCurrentShift.updateValue(shift);
    }

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    public abstract void updateFinalShift();

    protected PagedOrientationHandler getOrientationHandler() {
        return mTaskViewSimulator.getOrientationState().getOrientationHandler();
    }

    protected abstract class HomeAnimationFactory {
        protected float mSwipeVelocity;

        public @NonNull RectF getWindowTargetRect() {
            PagedOrientationHandler orientationHandler = getOrientationHandler();
            DeviceProfile dp = mDp;
            final int halfIconSize = dp.iconSizePx / 2;
            float primaryDimension = orientationHandler
                    .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
            float secondaryDimension = orientationHandler
                    .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
            final float targetX =  primaryDimension / 2f;
            final float targetY = secondaryDimension - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                    targetX + halfIconSize, targetY + halfIconSize);
        }

        /** Returns the corner radius of the window at the end of the animation. */
        public float getEndRadius(RectF cropRectF) {
            return cropRectF.width() / 2f;
        }

        public abstract @NonNull AnimatorPlaybackController createActivityAnimationToHome();

        public void setSwipeVelocity(float velocity) {
            mSwipeVelocity = velocity;
        }

        public void playAtomicAnimation(float velocity) {
            // No-op
        }

        public boolean shouldPlayAtomicWorkspaceReveal() {
            return true;
        }

        public void setAnimation(RectFSpringAnim anim) { }

        public boolean keepWindowOpaque() { return false; }

        public void update(@Nullable AppCloseConfig config, RectF currentRect, float progress,
                float radius) { }

        public void onCancel() { }

        /**
         * @return {@code true} if this factory supports animating an Activity to PiP window on
         * swiping up to home.
         */
        public boolean supportSwipePipToHome() {
            return false;
        }

        /**
         * @param progress The progress of the animation to the home screen.
         * @return The current alpha to set on the animating app window.
         */
        protected float getWindowAlpha(float progress) {
            // Alpha interpolates between [1, 0] between progress values [start, end]
            final float start = 0f;
            final float end = 0.85f;

            if (progress <= start) {
                return 1f;
            }
            if (progress >= end) {
                return 0f;
            }
            return Utilities.mapToRange(progress, start, end, 1, 0, ACCEL_1_5);
        }
    }

    /**
     * Update with start progress for window animation to home.
     * @param outMatrix {@link Matrix} to map a rect in Launcher space to window space.
     * @param startProgress The progress of {@link #mCurrentShift} to start thw window from.
     * @return {@link RectF} represents the bounds as starting point in window space.
     */
    protected RectF updateProgressForStartRect(Matrix outMatrix, float startProgress) {
        mCurrentShift.updateValue(startProgress);
        mTaskViewSimulator.apply(mTransformParams.setProgress(startProgress));
        RectF cropRectF = new RectF(mTaskViewSimulator.getCurrentCropRect());

        mTaskViewSimulator.applyWindowToHomeRotation(outMatrix);

        final RectF startRect = new RectF(cropRectF);
        mTaskViewSimulator.getCurrentMatrix().mapRect(startRect);
        return startRect;
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();

        Matrix homeToWindowPositionMap = new Matrix();
        final RectF startRect = updateProgressForStartRect(homeToWindowPositionMap, startProgress);
        RectF cropRectF = new RectF(mTaskViewSimulator.getCurrentCropRect());

        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        Matrix windowToHomePositionMap = new Matrix();
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);

        RectFSpringAnim anim;
        if (PROTOTYPE_APP_CLOSE.get()) {
            anim = new RectFSpringAnim2(startRect, targetRect, mContext,
                    mTaskViewSimulator.getCurrentCornerRadius(),
                    homeAnimationFactory.getEndRadius(cropRectF));
        } else {
            anim = new RectFSpringAnim(startRect, targetRect, mContext);
        }
        homeAnimationFactory.setAnimation(anim);

        SpringAnimationRunner runner = new SpringAnimationRunner(
                homeAnimationFactory, cropRectF, homeToWindowPositionMap);
        anim.addOnUpdateListener(runner);
        anim.addAnimatorListener(runner);
        return anim;
    }

    protected class SpringAnimationRunner extends AnimationSuccessListener
            implements RectFSpringAnim.OnUpdateListener, BuilderProxy {

        final Rect mCropRect = new Rect();
        final Matrix mMatrix = new Matrix();

        final RectF mWindowCurrentRect = new RectF();
        final Matrix mHomeToWindowPositionMap;
        final HomeAnimationFactory mAnimationFactory;

        final AnimatorPlaybackController mHomeAnim;
        final RectF mCropRectF;

        final float mStartRadius;
        final float mEndRadius;

        SpringAnimationRunner(HomeAnimationFactory factory, RectF cropRectF,
                Matrix homeToWindowPositionMap) {
            mAnimationFactory = factory;
            mHomeAnim = factory.createActivityAnimationToHome();
            mCropRectF = cropRectF;
            mHomeToWindowPositionMap = homeToWindowPositionMap;

            cropRectF.roundOut(mCropRect);

            // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
            // rounding at the end of the animation.
            mStartRadius = mTaskViewSimulator.getCurrentCornerRadius();
            mEndRadius = factory.getEndRadius(cropRectF);
        }

        @Override
        public void onUpdate(@Nullable AppCloseConfig config, RectF currentRect, float progress) {
            mHomeAnim.setPlayFraction(progress);
            mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, currentRect);

            mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
            float cornerRadius = Utilities.mapRange(progress, mStartRadius, mEndRadius);
            float alpha = mAnimationFactory.getWindowAlpha(progress);
            if (config != null && PROTOTYPE_APP_CLOSE.get()) {
                alpha = config.getWindowAlpha();
                cornerRadius = config.getCornerRadius();
            }
            if (mAnimationFactory.keepWindowOpaque()) {
                alpha = 1f;
            }
            mTransformParams
                    .setTargetAlpha(alpha)
                    .setCornerRadius(cornerRadius);
            mTransformParams.applySurfaceParams(mTransformParams.createSurfaceParams(this));
            mAnimationFactory.update(config, currentRect, progress,
                    mMatrix.mapRadius(cornerRadius));
        }

        @Override
        public void onBuildTargetParams(
                Builder builder, RemoteAnimationTargetCompat app, TransformParams params) {
            builder.withMatrix(mMatrix)
                    .withWindowCrop(mCropRect)
                    .withCornerRadius(params.getCornerRadius());
        }

        @Override
        public void onCancel() {
            mAnimationFactory.onCancel();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mHomeAnim.dispatchOnStart();
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            mHomeAnim.getAnimationPlayer().end();
        }
    }

    public interface RunningWindowAnim {
        void end();

        void cancel();

        static RunningWindowAnim wrap(Animator animator) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    animator.end();
                }

                @Override
                public void cancel() {
                    animator.cancel();
                }
            };
        }

        static RunningWindowAnim wrap(RectFSpringAnim rectFSpringAnim) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    rectFSpringAnim.end();
                }

                @Override
                public void cancel() {
                    rectFSpringAnim.cancel();
                }
            };
        }
    }
}
