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

package com.android.launcher3.notification;

import android.animation.Animator;
import android.content.Context;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Utility class to calculate general fling animation when the finger is released.
 *
 * This class was copied from com.android.systemui.statusbar.
 */
public class FlingAnimationUtils {

    private static final float LINEAR_OUT_SLOW_IN_X2 = 0.35f;
    private static final float LINEAR_OUT_SLOW_IN_X2_MAX = 0.68f;
    private static final float LINEAR_OUT_FASTER_IN_X2 = 0.5f;
    private static final float LINEAR_OUT_FASTER_IN_Y2_MIN = 0.4f;
    private static final float LINEAR_OUT_FASTER_IN_Y2_MAX = 0.5f;
    private static final float MIN_VELOCITY_DP_PER_SECOND = 250;
    private static final float HIGH_VELOCITY_DP_PER_SECOND = 3000;

    private static final float LINEAR_OUT_SLOW_IN_START_GRADIENT = 0.75f;
    private final float mSpeedUpFactor;
    private final float mY2;

    private float mMinVelocityPxPerSecond;
    private float mMaxLengthSeconds;
    private float mHighVelocityPxPerSecond;
    private float mLinearOutSlowInX2;

    private AnimatorProperties mAnimatorProperties = new AnimatorProperties();
    private PathInterpolator mInterpolator;
    private float mCachedStartGradient = -1;
    private float mCachedVelocityFactor = -1;

    public FlingAnimationUtils(Context ctx, float maxLengthSeconds) {
        this(ctx, maxLengthSeconds, 0.0f);
    }

    /**
     * @param maxLengthSeconds the longest duration an animation can become in seconds
     * @param speedUpFactor a factor from 0 to 1 how much the slow down should be shifted towards
     *                      the end of the animation. 0 means it's at the beginning and no
     *                      acceleration will take place.
     */
    public FlingAnimationUtils(Context ctx, float maxLengthSeconds, float speedUpFactor) {
        this(ctx, maxLengthSeconds, speedUpFactor, -1.0f, 1.0f);
    }

    /**
     * @param maxLengthSeconds the longest duration an animation can become in seconds
     * @param speedUpFactor a factor from 0 to 1 how much the slow down should be shifted towards
     *                      the end of the animation. 0 means it's at the beginning and no
     *                      acceleration will take place.
     * @param x2 the x value to take for the second point of the bezier spline. If a value below 0
     *           is provided, the value is automatically calculated.
     * @param y2 the y value to take for the second point of the bezier spline
     */
    public FlingAnimationUtils(Context ctx, float maxLengthSeconds, float speedUpFactor, float x2,
            float y2) {
        mMaxLengthSeconds = maxLengthSeconds;
        mSpeedUpFactor = speedUpFactor;
        if (x2 < 0) {
            mLinearOutSlowInX2 = interpolate(LINEAR_OUT_SLOW_IN_X2,
                    LINEAR_OUT_SLOW_IN_X2_MAX,
                    mSpeedUpFactor);
        } else {
            mLinearOutSlowInX2 = x2;
        }
        mY2 = y2;

        mMinVelocityPxPerSecond
                = MIN_VELOCITY_DP_PER_SECOND * ctx.getResources().getDisplayMetrics().density;
        mHighVelocityPxPerSecond
                = HIGH_VELOCITY_DP_PER_SECOND * ctx.getResources().getDisplayMetrics().density;
    }

    private static float interpolate(float start, float end, float amount) {
        return start * (1.0f - amount) + end * amount;
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     */
    public void apply(Animator animator, float currValue, float endValue, float velocity) {
        apply(animator, currValue, endValue, velocity, Math.abs(endValue - currValue));
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     */
    public void apply(ViewPropertyAnimator animator, float currValue, float endValue,
            float velocity) {
        apply(animator, currValue, endValue, velocity, Math.abs(endValue - currValue));
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     * @param maxDistance the maximum distance for this interaction; the maximum animation length
     *                    gets multiplied by the ratio between the actual distance and this value
     */
    public void apply(Animator animator, float currValue, float endValue, float velocity,
            float maxDistance) {
        AnimatorProperties properties = getProperties(currValue, endValue, velocity,
                maxDistance);
        animator.setDuration(properties.duration);
        animator.setInterpolator(properties.interpolator);
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     * @param maxDistance the maximum distance for this interaction; the maximum animation length
     *                    gets multiplied by the ratio between the actual distance and this value
     */
    public void apply(ViewPropertyAnimator animator, float currValue, float endValue,
            float velocity, float maxDistance) {
        AnimatorProperties properties = getProperties(currValue, endValue, velocity,
                maxDistance);
        animator.setDuration(properties.duration);
        animator.setInterpolator(properties.interpolator);
    }

    private AnimatorProperties getProperties(float currValue,
            float endValue, float velocity, float maxDistance) {
        float maxLengthSeconds = (float) (mMaxLengthSeconds
                * Math.sqrt(Math.abs(endValue - currValue) / maxDistance));
        float diff = Math.abs(endValue - currValue);
        float velAbs = Math.abs(velocity);
        float velocityFactor = mSpeedUpFactor == 0.0f
                ? 1.0f : Math.min(velAbs / HIGH_VELOCITY_DP_PER_SECOND, 1.0f);
        float startGradient = interpolate(LINEAR_OUT_SLOW_IN_START_GRADIENT,
                mY2 / mLinearOutSlowInX2, velocityFactor);
        float durationSeconds = startGradient * diff / velAbs;
        Interpolator slowInInterpolator = getInterpolator(startGradient, velocityFactor);
        if (durationSeconds <= maxLengthSeconds) {
            mAnimatorProperties.interpolator = slowInInterpolator;
        } else if (velAbs >= mMinVelocityPxPerSecond) {

            // Cross fade between fast-out-slow-in and linear interpolator with current velocity.
            durationSeconds = maxLengthSeconds;
            VelocityInterpolator velocityInterpolator
                    = new VelocityInterpolator(durationSeconds, velAbs, diff);
            InterpolatorInterpolator superInterpolator = new InterpolatorInterpolator(
                    velocityInterpolator, slowInInterpolator, Interpolators.LINEAR_OUT_SLOW_IN);
            mAnimatorProperties.interpolator = superInterpolator;
        } else {

            // Just use a normal interpolator which doesn't take the velocity into account.
            durationSeconds = maxLengthSeconds;
            mAnimatorProperties.interpolator = Interpolators.FAST_OUT_SLOW_IN;
        }
        mAnimatorProperties.duration = (long) (durationSeconds * 1000);
        return mAnimatorProperties;
    }

    private Interpolator getInterpolator(float startGradient, float velocityFactor) {
        if (startGradient != mCachedStartGradient
                || velocityFactor != mCachedVelocityFactor) {
            float speedup = mSpeedUpFactor * (1.0f - velocityFactor);
            mInterpolator = new PathInterpolator(speedup,
                    speedup * startGradient,
                    mLinearOutSlowInX2, mY2);
            mCachedStartGradient = startGradient;
            mCachedVelocityFactor = velocityFactor;
        }
        return mInterpolator;
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion for the case when the animation is making something
     * disappear.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     * @param maxDistance the maximum distance for this interaction; the maximum animation length
     *                    gets multiplied by the ratio between the actual distance and this value
     */
    public void applyDismissing(Animator animator, float currValue, float endValue,
            float velocity, float maxDistance) {
        AnimatorProperties properties = getDismissingProperties(currValue, endValue, velocity,
                maxDistance);
        animator.setDuration(properties.duration);
        animator.setInterpolator(properties.interpolator);
    }

    /**
     * Applies the interpolator and length to the animator, such that the fling animation is
     * consistent with the finger motion for the case when the animation is making something
     * disappear.
     *
     * @param animator the animator to apply
     * @param currValue the current value
     * @param endValue the end value of the animator
     * @param velocity the current velocity of the motion
     * @param maxDistance the maximum distance for this interaction; the maximum animation length
     *                    gets multiplied by the ratio between the actual distance and this value
     */
    public void applyDismissing(ViewPropertyAnimator animator, float currValue, float endValue,
            float velocity, float maxDistance) {
        AnimatorProperties properties = getDismissingProperties(currValue, endValue, velocity,
                maxDistance);
        animator.setDuration(properties.duration);
        animator.setInterpolator(properties.interpolator);
    }

    private AnimatorProperties getDismissingProperties(float currValue, float endValue,
            float velocity, float maxDistance) {
        float maxLengthSeconds = (float) (mMaxLengthSeconds
                * Math.pow(Math.abs(endValue - currValue) / maxDistance, 0.5f));
        float diff = Math.abs(endValue - currValue);
        float velAbs = Math.abs(velocity);
        float y2 = calculateLinearOutFasterInY2(velAbs);

        float startGradient = y2 / LINEAR_OUT_FASTER_IN_X2;
        Interpolator mLinearOutFasterIn = new PathInterpolator(0, 0, LINEAR_OUT_FASTER_IN_X2, y2);
        float durationSeconds = startGradient * diff / velAbs;
        if (durationSeconds <= maxLengthSeconds) {
            mAnimatorProperties.interpolator = mLinearOutFasterIn;
        } else if (velAbs >= mMinVelocityPxPerSecond) {

            // Cross fade between linear-out-faster-in and linear interpolator with current
            // velocity.
            durationSeconds = maxLengthSeconds;
            VelocityInterpolator velocityInterpolator
                    = new VelocityInterpolator(durationSeconds, velAbs, diff);
            InterpolatorInterpolator superInterpolator = new InterpolatorInterpolator(
                    velocityInterpolator, mLinearOutFasterIn, Interpolators.LINEAR_OUT_SLOW_IN);
            mAnimatorProperties.interpolator = superInterpolator;
        } else {

            // Just use a normal interpolator which doesn't take the velocity into account.
            durationSeconds = maxLengthSeconds;
            mAnimatorProperties.interpolator = Interpolators.FAST_OUT_LINEAR_IN;
        }
        mAnimatorProperties.duration = (long) (durationSeconds * 1000);
        return mAnimatorProperties;
    }

    /**
     * Calculates the y2 control point for a linear-out-faster-in path interpolator depending on the
     * velocity. The faster the velocity, the more "linear" the interpolator gets.
     *
     * @param velocity the velocity of the gesture.
     * @return the y2 control point for a cubic bezier path interpolator
     */
    private float calculateLinearOutFasterInY2(float velocity) {
        float t = (velocity - mMinVelocityPxPerSecond)
                / (mHighVelocityPxPerSecond - mMinVelocityPxPerSecond);
        t = Math.max(0, Math.min(1, t));
        return (1 - t) * LINEAR_OUT_FASTER_IN_Y2_MIN + t * LINEAR_OUT_FASTER_IN_Y2_MAX;
    }

    /**
     * @return the minimum velocity a gesture needs to have to be considered a fling
     */
    public float getMinVelocityPxPerSecond() {
        return mMinVelocityPxPerSecond;
    }

    /**
     * An interpolator which interpolates two interpolators with an interpolator.
     */
    private static final class InterpolatorInterpolator implements Interpolator {

        private Interpolator mInterpolator1;
        private Interpolator mInterpolator2;
        private Interpolator mCrossfader;

        InterpolatorInterpolator(Interpolator interpolator1, Interpolator interpolator2,
                Interpolator crossfader) {
            mInterpolator1 = interpolator1;
            mInterpolator2 = interpolator2;
            mCrossfader = crossfader;
        }

        @Override
        public float getInterpolation(float input) {
            float t = mCrossfader.getInterpolation(input);
            return (1 - t) * mInterpolator1.getInterpolation(input)
                    + t * mInterpolator2.getInterpolation(input);
        }
    }

    /**
     * An interpolator which interpolates with a fixed velocity.
     */
    private static final class VelocityInterpolator implements Interpolator {

        private float mDurationSeconds;
        private float mVelocity;
        private float mDiff;

        private VelocityInterpolator(float durationSeconds, float velocity, float diff) {
            mDurationSeconds = durationSeconds;
            mVelocity = velocity;
            mDiff = diff;
        }

        @Override
        public float getInterpolation(float input) {
            float time = input * mDurationSeconds;
            return time * mVelocity / mDiff;
        }
    }

    private static class AnimatorProperties {
        Interpolator interpolator;
        long duration;
    }

}
