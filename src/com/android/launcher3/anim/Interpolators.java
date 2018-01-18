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

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
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
    public static final Interpolator DEACCEL_2 = new DecelerateInterpolator(2);
    public static final Interpolator DEACCEL_2_5 = new DecelerateInterpolator(2.5f);
    public static final Interpolator DEACCEL_3 = new DecelerateInterpolator(3f);

    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    public static final Interpolator AGGRESSIVE_EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /**
     * Inversion of zInterpolate, compounded with an ease-out.
     */
    public static final Interpolator ZOOM_IN = new Interpolator() {

        private static final float FOCAL_LENGTH = 0.35f;

        @Override
        public float getInterpolation(float v) {
            return DEACCEL_3.getInterpolation(1 - zInterpolate(1 - v));
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
}