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
package com.android.quickstep.util;

import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.FlingSpringAnim;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.RemoteAnimationTargets.ReleaseCheck;
import com.android.systemui.plugins.ResourceProvider;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;


/**
 * Applies spring forces to animate from a starting rect to a target rect,
 * while providing update callbacks to the caller.
 */
public class RectFSpringAnim extends ReleaseCheck {

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_CENTER_X =
            new FloatPropertyCompat<RectFSpringAnim>("rectCenterXSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentCenterX;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float currentCenterX) {
                    anim.mCurrentCenterX = currentCenterX;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_Y =
            new FloatPropertyCompat<RectFSpringAnim>("rectYSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentY;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float y) {
                    anim.mCurrentY = y;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_SCALE_PROGRESS =
            new FloatPropertyCompat<RectFSpringAnim>("rectScaleProgress") {
                @Override
                public float getValue(RectFSpringAnim object) {
                    return object.mCurrentScaleProgress;
                }

                @Override
                public void setValue(RectFSpringAnim object, float value) {
                    object.mCurrentScaleProgress = value;
                    object.onUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();

    private float mCurrentCenterX;
    private float mCurrentY;
    // If true, tracking the bottom of the rects, else tracking the top.
    private float mCurrentScaleProgress;
    private FlingSpringAnim mRectXAnim;
    private FlingSpringAnim mRectYAnim;
    private SpringAnimation mRectXSpring;
    private SpringAnimation mRectYSpring;
    private SpringAnimation mRectScaleAnim;
    private boolean mAnimsStarted;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;

    private float mMinVisChange;
    private int mMaxVelocityPxPerS;

    /**
     * Indicates which part of the start & target rects we are interpolating between.
     */
    public static final int TRACKING_TOP = 0;
    public static final int TRACKING_CENTER = 1;
    public static final int TRACKING_BOTTOM = 2;

    @Retention(SOURCE)
    @IntDef(value = {TRACKING_TOP,
                    TRACKING_CENTER,
                    TRACKING_BOTTOM})
    public @interface Tracking{}

    @Tracking
    public final int mTracking;
    protected final float mStiffnessX;
    protected final float mStiffnessY;
    protected final float mDampingX;
    protected final float mDampingY;
    protected final float mRectStiffness;

    public RectFSpringAnim(SpringConfig config) {
        mStartRect = config.startRect;
        mTargetRect = config.targetRect;
        mCurrentCenterX = mStartRect.centerX();

        mMinVisChange = config.minVisChange;
        mMaxVelocityPxPerS = config.maxVelocityPxPerS;
        setCanRelease(true);

        mTracking = config.tracking;
        mStiffnessX = config.stiffnessX;
        mStiffnessY = config.stiffnessY;
        mDampingX = config.dampingX;
        mDampingY = config.dampingY;
        mRectStiffness = config.rectStiffness;

        mCurrentY = getTrackedYFromRect(mStartRect);
    }

    private float getTrackedYFromRect(RectF rect) {
        switch (mTracking) {
            case TRACKING_TOP:
                return rect.top;
            case TRACKING_BOTTOM:
                return rect.bottom;
            case TRACKING_CENTER:
            default:
                return rect.centerY();
        }
    }

    public void onTargetPositionChanged() {
        if (enableScalingRevealHomeAnimation()) {
            if (isEnded()) {
                return;
            }

            if (mRectXSpring != null) {
                mRectXSpring.animateToFinalPosition(mTargetRect.centerX());
                mRectXAnimEnded = false;
            }

            if (mRectYSpring != null) {
                switch (mTracking) {
                    case TRACKING_TOP:
                        mRectYSpring.animateToFinalPosition(mTargetRect.top);
                        break;
                    case TRACKING_BOTTOM:
                        mRectYSpring.animateToFinalPosition(mTargetRect.bottom);
                        break;
                    case TRACKING_CENTER:
                        mRectYSpring.animateToFinalPosition(mTargetRect.centerY());
                        break;
                }
                mRectYAnimEnded = false;
            }
        } else {
            if (mRectXAnim != null && mRectXAnim.getTargetPosition() != mTargetRect.centerX()) {
                mRectXAnim.updatePosition(mCurrentCenterX, mTargetRect.centerX());
            }

            if (mRectYAnim != null) {
                switch (mTracking) {
                    case TRACKING_TOP:
                        if (mRectYAnim.getTargetPosition() != mTargetRect.top) {
                            mRectYAnim.updatePosition(mCurrentY, mTargetRect.top);
                        }
                        break;
                    case TRACKING_BOTTOM:
                        if (mRectYAnim.getTargetPosition() != mTargetRect.bottom) {
                            mRectYAnim.updatePosition(mCurrentY, mTargetRect.bottom);
                        }
                        break;
                    case TRACKING_CENTER:
                        if (mRectYAnim.getTargetPosition() != mTargetRect.centerY()) {
                            mRectYAnim.updatePosition(mCurrentY, mTargetRect.centerY());
                        }
                        break;
                }
            }
        }
    }

    public void addOnUpdateListener(OnUpdateListener onUpdateListener) {
        mOnUpdateListeners.add(onUpdateListener);
    }

    public void addAnimatorListener(Animator.AnimatorListener animatorListener) {
        mAnimatorListeners.add(animatorListener);
    }

    /**
     * Starts the fling/spring animation.
     * @param context The activity context.
     * @param velocityPxPerMs Velocity of swipe in px/ms.
     */
    public void start(Context context, @Nullable DeviceProfile profile, PointF velocityPxPerMs) {
        // Only tell caller that we ended if both x and y animations have ended.
        OnAnimationEndListener onXEndListener = ((animation, canceled, centerX, velocityX) -> {
            mRectXAnimEnded = true;
            maybeOnEnd();
        });
        OnAnimationEndListener onYEndListener = ((animation, canceled, centerY, velocityY) -> {
            mRectYAnimEnded = true;
            maybeOnEnd();
        });

        float xVelocityPxPerS = velocityPxPerMs.x * 1000;
        float yVelocityPxPerS = velocityPxPerMs.y * 1000;
        float startX = mCurrentCenterX;
        float endX = mTargetRect.centerX();
        float startY = mCurrentY;
        float endY = getTrackedYFromRect(mTargetRect);
        float minVisibleChange = Math.abs(1f / mStartRect.height());

        if (enableScalingRevealHomeAnimation()) {
            ResourceProvider rp = DynamicResource.provider(context);
            long minVelocityXPxPerS = rp.getInt(R.dimen.swipe_up_min_velocity_x_px_per_s);
            long maxVelocityXPxPerS = rp.getInt(R.dimen.swipe_up_max_velocity_x_px_per_s);
            long minVelocityYPxPerS = rp.getInt(R.dimen.swipe_up_min_velocity_y_px_per_s);
            long maxVelocityYPxPerS = rp.getInt(R.dimen.swipe_up_max_velocity_y_px_per_s);
            float fallOffFactor = rp.getFloat(R.dimen.swipe_up_max_velocity_fall_off_factor);

            // We want the actual initial velocity to never dip below the minimum, and to taper off
            // once it's above the soft cap so that we can prevent the window from flying off
            // screen, while maintaining a natural feel.
            xVelocityPxPerS = adjustVelocity(
                    xVelocityPxPerS, minVelocityXPxPerS, maxVelocityXPxPerS, fallOffFactor);
            yVelocityPxPerS = adjustVelocity(
                    yVelocityPxPerS, minVelocityYPxPerS, maxVelocityYPxPerS, fallOffFactor);

            float stiffnessX = rp.getFloat(R.dimen.swipe_up_rect_x_stiffness);
            float dampingX = rp.getFloat(R.dimen.swipe_up_rect_x_damping_ratio);
            mRectXSpring =
                    new SpringAnimation(this, RECT_CENTER_X)
                            .setSpring(
                                    new SpringForce(endX)
                                            .setStiffness(stiffnessX)
                                            .setDampingRatio(dampingX)
                            ).setStartValue(startX)
                            .setStartVelocity(xVelocityPxPerS)
                            .addEndListener(onXEndListener);

            float stiffnessY = rp.getFloat(R.dimen.swipe_up_rect_y_stiffness);
            float dampingY = rp.getFloat(R.dimen.swipe_up_rect_y_damping_ratio);
            mRectYSpring =
                    new SpringAnimation(this, RECT_Y)
                            .setSpring(
                                    new SpringForce(endY)
                                            .setStiffness(stiffnessY)
                                            .setDampingRatio(dampingY)
                            )
                            .setStartValue(startY)
                            .setStartVelocity(yVelocityPxPerS)
                            .addEndListener(onYEndListener);

            float stiffnessZ = rp.getFloat(R.dimen.swipe_up_rect_scale_stiffness_v2);
            float dampingZ = rp.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio_v2);
            mRectScaleAnim =
                    new SpringAnimation(this, RECT_SCALE_PROGRESS)
                            .setSpring(
                                    new SpringForce(1f)
                                            .setStiffness(stiffnessZ)
                                            .setDampingRatio(dampingZ))
                            .setStartVelocity(velocityPxPerMs.y * minVisibleChange)
                            .setMaxValue(1f)
                            .setMinimumVisibleChange(minVisibleChange)
                            .addEndListener((animation, canceled, value, velocity) -> {
                                mRectScaleAnimEnded = true;
                                maybeOnEnd();
                            });

            setCanRelease(false);
            mAnimsStarted = true;

            mRectXSpring.start();
            mRectYSpring.start();
        } else {
            // We dampen the user velocity here to keep the natural feeling and to prevent the
            // rect from straying too from a linear path.
            final float dampedXVelocityPxPerS = OverScroll.dampedScroll(
                    Math.abs(xVelocityPxPerS), mMaxVelocityPxPerS) * Math.signum(xVelocityPxPerS);
            final float dampedYVelocityPxPerS = OverScroll.dampedScroll(
                    Math.abs(yVelocityPxPerS), mMaxVelocityPxPerS) * Math.signum(yVelocityPxPerS);

            float minXValue = Math.min(startX, endX);
            float maxXValue = Math.max(startX, endX);

            mRectXAnim = new FlingSpringAnim(this, context, RECT_CENTER_X, startX, endX,
                    dampedXVelocityPxPerS, mMinVisChange, minXValue, maxXValue, mDampingX,
                    mStiffnessX, onXEndListener);

            float minYValue = Math.min(startY, endY);
            float maxYValue = Math.max(startY, endY);
            mRectYAnim = new FlingSpringAnim(this, context, RECT_Y, startY, endY,
                    dampedYVelocityPxPerS, mMinVisChange, minYValue, maxYValue, mDampingY,
                    mStiffnessY, onYEndListener);

            ResourceProvider rp = DynamicResource.provider(context);
            float damping = rp.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio);

            // Increase the stiffness for devices where we want the window size to transform
            // quicker.
            boolean shouldUseHigherStiffness = profile != null
                    && (profile.isLandscape || profile.isTablet);
            float stiffness = shouldUseHigherStiffness
                    ? rp.getFloat(R.dimen.swipe_up_rect_scale_higher_stiffness)
                    : rp.getFloat(R.dimen.swipe_up_rect_scale_stiffness);

            mRectScaleAnim = new SpringAnimation(this, RECT_SCALE_PROGRESS)
                    .setSpring(new SpringForce(1f)
                            .setDampingRatio(damping)
                            .setStiffness(stiffness))
                    .setStartVelocity(velocityPxPerMs.y * minVisibleChange)
                    .setMaxValue(1f)
                    .setMinimumVisibleChange(minVisibleChange)
                    .addEndListener((animation, canceled, value, velocity) -> {
                        mRectScaleAnimEnded = true;
                        maybeOnEnd();
                    });

            setCanRelease(false);
            mAnimsStarted = true;

            mRectXAnim.start();
            mRectYAnim.start();
        }

        mRectScaleAnim.start();
        for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
            animatorListener.onAnimationStart(null);
        }
    }

    public void end() {
        if (mAnimsStarted) {
            if (enableScalingRevealHomeAnimation()) {
                if (mRectXSpring.canSkipToEnd()) {
                    mRectXSpring.skipToEnd();
                }
                if (mRectYSpring.canSkipToEnd()) {
                    mRectYSpring.skipToEnd();
                }
            } else {
                mRectXAnim.end();
                mRectYAnim.end();
            }
            if (mRectScaleAnim.canSkipToEnd()) {
                mRectScaleAnim.skipToEnd();
            }
            mCurrentScaleProgress = mRectScaleAnim.getSpring().getFinalPosition();

            // Ensures that we end the animation with the final values.
            mRectXAnimEnded = false;
            mRectYAnimEnded = false;
            mRectScaleAnimEnded = false;
            onUpdate();
        }

        mRectXAnimEnded = true;
        mRectYAnimEnded = true;
        mRectScaleAnimEnded = true;
        maybeOnEnd();
    }

    private boolean isEnded() {
        return mRectXAnimEnded && mRectYAnimEnded && mRectScaleAnimEnded;
    }

    private void onUpdate() {
        if (isEnded()) {
            // Prevent further updates from being called. This can happen between callbacks for
            // ending the x/y/scale animations.
            return;
        }

        if (!mOnUpdateListeners.isEmpty()) {
            float currentWidth = Utilities.mapRange(mCurrentScaleProgress, mStartRect.width(),
                    mTargetRect.width());
            float currentHeight = Utilities.mapRange(mCurrentScaleProgress, mStartRect.height(),
                    mTargetRect.height());
            switch (mTracking) {
                case TRACKING_TOP:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY + currentHeight);
                    break;
                case TRACKING_BOTTOM:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY - currentHeight,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY);
                    break;
                case TRACKING_CENTER:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY - currentHeight / 2,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY + currentHeight / 2);
                    break;
            }
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onUpdate(mCurrentRect, mCurrentScaleProgress);
            }
        }
    }

    private void maybeOnEnd() {
        if (mAnimsStarted && isEnded()) {
            mAnimsStarted = false;
            setCanRelease(true);
            for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
                animatorListener.onAnimationEnd(null);
            }
        }
    }

    public void cancel() {
        if (mAnimsStarted) {
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onCancel();
            }
        }
        end();
    }

    /**
     * Modify the given velocity so that it's never below the minimum value, and falls off by the
     * given factor once it goes above the maximum value.
     * In order for the max soft cap to be enforced, the fall-off factor must be >1.
     */
    private static float adjustVelocity(float velocity, long min, long max, float factor) {
        float sign = Math.signum(velocity);
        float magnitude = Math.abs(velocity);

        // If the absolute velocity is less than the min, bump it up.
        if (magnitude < min) {
            return min * sign;
        }

        // If the absolute velocity falls between min and max, or the fall-off factor is invalid,
        // do nothing.
        if (magnitude <= max || factor <= 1) {
            return velocity;
        }

        // Scale the excess velocity by the fall-off factor.
        float excess = magnitude - max;
        float scaled = (float) Math.pow(excess, 1f / factor);
        return (max + scaled) * sign;
    }

    public interface OnUpdateListener {
        /**
         * Called when an update is made to the animation.
         * @param currentRect The rect of the window.
         * @param progress [0, 1] The progress of the rect scale animation.
         */
        void onUpdate(RectF currentRect, float progress);

        default void onCancel() { }
    }

    private abstract static class SpringConfig {
        protected RectF startRect;
        protected RectF targetRect;
        protected @Tracking int tracking;
        protected float stiffnessX;
        protected float stiffnessY;
        protected float dampingX;
        protected float dampingY;
        protected float rectStiffness;
        protected float minVisChange;
        protected int maxVelocityPxPerS;

        private SpringConfig(Context context, RectF start, RectF target) {
            startRect = start;
            targetRect = target;

            ResourceProvider rp = DynamicResource.provider(context);
            minVisChange = rp.getDimension(R.dimen.swipe_up_fling_min_visible_change);
            maxVelocityPxPerS = (int) rp.getDimension(R.dimen.swipe_up_max_velocity);
        }
    }

    /**
     * Standard spring configuration parameters.
     */
    public static class DefaultSpringConfig extends SpringConfig {

        public DefaultSpringConfig(Context context, DeviceProfile deviceProfile,
                RectF startRect, RectF targetRect) {
            super(context, startRect, targetRect);

            ResourceProvider rp = DynamicResource.provider(context);
            tracking = getDefaultTracking(deviceProfile);
            stiffnessX = rp.getFloat(R.dimen.swipe_up_rect_xy_stiffness);
            stiffnessY = rp.getFloat(R.dimen.swipe_up_rect_xy_stiffness);
            dampingX = rp.getFloat(R.dimen.swipe_up_rect_xy_damping_ratio);
            dampingY = rp.getFloat(R.dimen.swipe_up_rect_xy_damping_ratio);

            this.startRect = startRect;
            this.targetRect = targetRect;

            // Increase the stiffness for devices where we want the window size to transform
            // quicker.
            boolean shouldUseHigherStiffness = deviceProfile != null
                    && (deviceProfile.isLandscape || deviceProfile.isTablet);
            rectStiffness = shouldUseHigherStiffness
                    ? rp.getFloat(R.dimen.swipe_up_rect_scale_higher_stiffness)
                    : rp.getFloat(R.dimen.swipe_up_rect_scale_stiffness);
        }

        private @Tracking int getDefaultTracking(@Nullable DeviceProfile deviceProfile) {
            @Tracking int tracking;
            if (deviceProfile == null) {
                tracking = startRect.bottom < targetRect.bottom
                        ? TRACKING_BOTTOM
                        : TRACKING_TOP;
            } else {
                int heightPx = deviceProfile.heightPx;
                Rect padding = deviceProfile.workspacePadding;

                final float topThreshold = heightPx / 3f;
                final float bottomThreshold = deviceProfile.heightPx - padding.bottom;

                if (targetRect.bottom > bottomThreshold) {
                    tracking = TRACKING_BOTTOM;
                } else if (targetRect.top < topThreshold) {
                    tracking = TRACKING_TOP;
                } else {
                    tracking = TRACKING_CENTER;
                }
            }
            return tracking;
        }
    }

    /**
     * Spring configuration parameters for Taskbar/Hotseat items on devices that have a taskbar.
     */
    public static class TaskbarHotseatSpringConfig extends SpringConfig {

        public TaskbarHotseatSpringConfig(Context context, RectF start, RectF target) {
            super(context, start, target);

            ResourceProvider rp = DynamicResource.provider(context);
            tracking = TRACKING_CENTER;
            stiffnessX = rp.getFloat(R.dimen.taskbar_swipe_up_rect_x_stiffness);
            stiffnessY = rp.getFloat(R.dimen.taskbar_swipe_up_rect_y_stiffness);
            dampingX = rp.getFloat(R.dimen.taskbar_swipe_up_rect_x_damping);
            dampingY = rp.getFloat(R.dimen.taskbar_swipe_up_rect_y_damping);
            rectStiffness = rp.getFloat(R.dimen.taskbar_swipe_up_rect_scale_stiffness);
        }
    }

}
