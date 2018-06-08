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

import android.graphics.Path;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;


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

    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    public static final Interpolator AGGRESSIVE_EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator AGGRESSIVE_EASE_IN_OUT = new PathInterpolator(0.6f,0, 0.4f, 1);

    public static final Interpolator EXAGGERATED_EASE;

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
}