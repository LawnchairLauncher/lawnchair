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

package com.android.launcher3.anim;

import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;

import android.graphics.Path;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import com.android.launcher3.Utilities;


/**
 * Common interpolators used in Launcher
 */
public class Interpolators {

    public static final Interpolator LINEAR = new LinearInterpolator();

    public static final Interpolator ACCEL = new AccelerateInterpolator();
    public static final Interpolator ACCEL_1_5 = new AccelerateInterpolator(1.5f);
    public static final Interpolator ACCEL_2 = new AccelerateInterpolator(2);

    public static final Interpolator DEACCEL = new DecelerateInterpolator();
    public static final Interpolator DEACCEL_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DEACCEL_1_7 = new DecelerateInterpolator(1.7f);
    public static final Interpolator DEACCEL_2 = new DecelerateInterpolator(2);
    public static final Interpolator DEACCEL_2_5 = new DecelerateInterpolator(2.5f);
    public static final Interpolator DEACCEL_3 = new DecelerateInterpolator(3f);

    public static final Interpolator ACCEL_DEACCEL = new AccelerateDecelerateInterpolator();

    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    public static final Interpolator AGGRESSIVE_EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator AGGRESSIVE_EASE_IN_OUT = new PathInterpolator(0.6f,0, 0.4f, 1);

    public static final Interpolator EXAGGERATED_EASE;

    private static final int MIN_SETTLE_DURATION = 200;
    private static final float OVERSHOOT_FACTOR = 0.9f;

    static {
        Path exaggeratedEase = new Path();
        exaggeratedEase.moveTo(0, 0);
        exaggeratedEase.cubicTo(0.05f, 0f, 0.133333f, 0.08f, 0.166666f, 0.4f);
        exaggeratedEase.cubicTo(0.225f, 0.94f, 0.5f, 1f, 1f, 1f);
        EXAGGERATED_EASE = new PathInterpolator(exaggeratedEase);
    }

    public static final Interpolator OVERSHOOT_1_2 = new OvershootInterpolator(1.2f);

    public static final Interpolator TOUCH_RESPONSE_INTERPOLATOR =
            new PathInterpolator(0.3f, 0f, 0.1f, 1f);

    /**
     * Inversion of ZOOM_OUT, compounded with an ease-out.
     */
    public static final Interpolator ZOOM_IN = new Interpolator() {
        @Override
        public float getInterpolation(float v) {
            return DEACCEL_3.getInterpolation(1 - ZOOM_OUT.getInterpolation(1 - v));
        }
    };

    public static final Interpolator ZOOM_OUT = new Interpolator() {

        private static final float FOCAL_LENGTH = 0.35f;

        @Override
        public float getInterpolation(float v) {
            return zInterpolate(v);
        }

        /**
         * This interpolator emulates the rate at which the perceived scale of an object changes
         * as its distance from a camera increases. When this interpolator is applied to a scale
         * animation on a view, it evokes the sense that the object is shrinking due to moving away
         * from the camera.
         */
        private float zInterpolate(float input) {
            return (1.0f - FOCAL_LENGTH / (FOCAL_LENGTH + input)) /
                    (1.0f - FOCAL_LENGTH / (FOCAL_LENGTH + 1.0f));
        }
    };

    public static final Interpolator SCROLL = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    };

    public static final Interpolator SCROLL_CUBIC = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t + 1;
        }
    };

    private static final float FAST_FLING_PX_MS = 10;

    public static Interpolator scrollInterpolatorForVelocity(float velocity) {
        return Math.abs(velocity) > FAST_FLING_PX_MS ? SCROLL : SCROLL_CUBIC;
    }

    /**
     * Create an OvershootInterpolator with tension directly related to the velocity (in px/ms).
     * @param velocity The start velocity of the animation we want to overshoot.
     */
    public static Interpolator overshootInterpolatorForVelocity(float velocity) {
        return new OvershootInterpolator(Math.min(Math.abs(velocity), 3f));
    }

    /**
     * Runs the given interpolator such that the entire progress is set between the given bounds.
     * That is, we set the interpolation to 0 until lowerBound and reach 1 by upperBound.
     */
    public static Interpolator clampToProgress(Interpolator interpolator, float lowerBound,
            float upperBound) {
        if (upperBound <= lowerBound) {
            throw new IllegalArgumentException("lowerBound must be less than upperBound");
        }
        return t -> {
            if (t < lowerBound) {
                return 0;
            }
            if (t > upperBound) {
                return 1;
            }
            return interpolator.getInterpolation((t - lowerBound) / (upperBound - lowerBound));
        };
    }

    /**
     * Runs the given interpolator such that the interpolated value is mapped to the given range.
     * This is useful, for example, if we only use this interpolator for part of the animation,
     * such as to take over a user-controlled animation when they let go.
     */
    public static Interpolator mapToProgress(Interpolator interpolator, float lowerBound,
            float upperBound) {
        return t -> Utilities.mapRange(interpolator.getInterpolation(t), lowerBound, upperBound);
    }

    /**
     * Computes parameters necessary for an overshoot effect.
     */
    public static class OvershootParams {
        public Interpolator interpolator;
        public float start;
        public float end;
        public long duration;

        /**
         * Given the input params, sets OvershootParams variables to be used by the caller.
         * @param startProgress The progress from 0 to 1 that the overshoot starts from.
         * @param overshootPastProgress The progress from 0 to 1 where we overshoot past (should
         *        either be equal to startProgress or endProgress, depending on if we want to
         *        overshoot immediately or only once we reach the end).
         * @param endProgress The final progress from 0 to 1 that we will settle to.
         * @param velocityPxPerMs The initial velocity that causes this overshoot.
         * @param totalDistancePx The distance against which progress is calculated.
         */
        public OvershootParams(float startProgress, float overshootPastProgress,
                float endProgress, float velocityPxPerMs, int totalDistancePx) {
            velocityPxPerMs = Math.abs(velocityPxPerMs);
            start = startProgress;
            int startPx = (int) (start * totalDistancePx);
            // Overshoot by about half a frame.
            float overshootBy = OVERSHOOT_FACTOR * velocityPxPerMs *
                    SINGLE_FRAME_MS / totalDistancePx / 2;
            overshootBy = Utilities.boundToRange(overshootBy, 0.02f, 0.15f);
            end = overshootPastProgress + overshootBy;
            int endPx = (int) (end  * totalDistancePx);
            int overshootDistance = endPx - startPx;
            // Calculate deceleration necessary to reach overshoot distance.
            // Formula: velocityFinal^2 = velocityInitial^2 + 2 * acceleration * distance
            //          0 = v^2 + 2ad (velocityFinal == 0)
            //          a = v^2 / -2d
            float decelerationPxPerMs = velocityPxPerMs * velocityPxPerMs / (2 * overshootDistance);
            // Calculate time necessary to reach peak of overshoot.
            // Formula: acceleration = velocity / time
            //          time = velocity / acceleration
            duration = (long) (velocityPxPerMs / decelerationPxPerMs);

            // Now that we're at the top of the overshoot, need to settle back to endProgress.
            float settleDistance = end - endProgress;
            int settleDistancePx = (int) (settleDistance * totalDistancePx);
            // Calculate time necessary for the settle.
            // Formula: distance = velocityInitial * time + 1/2 * acceleration * time^2
            //          d = 1/2at^2 (velocityInitial = 0, since we just stopped at the top)
            //          t = sqrt(2d/a)
            // Above formula assumes constant acceleration. Since we use ACCEL_DEACCEL, we actually
            // have acceleration to halfway then deceleration the rest. So the formula becomes:
            //          t = sqrt(d/a) * 2 (half the distance for accel, half for deaccel)
            long settleDuration = (long) Math.sqrt(settleDistancePx / decelerationPxPerMs) * 4;

            settleDuration = Math.max(MIN_SETTLE_DURATION, settleDuration);
            // How much of the animation to devote to playing the overshoot (the rest is for settle).
            float overshootFraction = (float) duration / (duration + settleDuration);
            duration += settleDuration;
            // Finally, create the interpolator, composed of two interpolators: an overshoot, which
            // reaches end > 1, and then a settle to endProgress.
            Interpolator overshoot = Interpolators.clampToProgress(DEACCEL, 0, overshootFraction);
            // The settle starts at 1, where 1 is the top of the overshoot, and maps to a fraction
            // such that final progress is endProgress. For example, if we overshot to 1.1 but want
            // to end at 1, we need to map to 1/1.1.
            Interpolator settle = Interpolators.clampToProgress(Interpolators.mapToProgress(
                    ACCEL_DEACCEL, 1, (endProgress - start) / (end - start)), overshootFraction, 1);
            interpolator = t -> t <= overshootFraction
                    ? overshoot.getInterpolation(t)
                    : settle.getInterpolation(t);
        }
    }
}
