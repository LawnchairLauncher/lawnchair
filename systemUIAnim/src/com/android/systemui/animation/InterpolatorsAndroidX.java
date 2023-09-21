/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.animation;

import android.graphics.Path;
import android.util.MathUtils;

import androidx.core.animation.AccelerateDecelerateInterpolator;
import androidx.core.animation.AccelerateInterpolator;
import androidx.core.animation.BounceInterpolator;
import androidx.core.animation.DecelerateInterpolator;
import androidx.core.animation.Interpolator;
import androidx.core.animation.LinearInterpolator;
import androidx.core.animation.PathInterpolator;

/**
 * Utility class to receive interpolators from. (androidx compatible version)
 *
 * This is the androidx compatible version of {@link Interpolators}. Make sure that changes made to
 * this class are also reflected in {@link Interpolators}.
 *
 * Using the androidx versions of {@link androidx.core.animation.ValueAnimator} or
 * {@link androidx.core.animation.ObjectAnimator} improves animation testability. This file provides
 * the androidx compatible versions of the interpolators defined in {@link Interpolators}.
 * AnimatorTestRule can be used in Tests to manipulate the animation under test (e.g. artificially
 * advancing the time).
 */
public class InterpolatorsAndroidX {

    /*
     * ============================================================================================
     * Emphasized interpolators.
     * ============================================================================================
     */

    /**
     * The default emphasized interpolator. Used for hero / emphasized movement of content.
     */
    public static final Interpolator EMPHASIZED = createEmphasizedInterpolator();

    /**
     * The accelerated emphasized interpolator. Used for hero / emphasized movement of content that
     * is disappearing e.g. when moving off screen.
     */
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(
            0.3f, 0f, 0.8f, 0.15f);

    /**
     * The decelerating emphasized interpolator. Used for hero / emphasized movement of content that
     * is appearing e.g. when coming from off screen
     */
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(
            0.05f, 0.7f, 0.1f, 1f);


    /*
     * ============================================================================================
     * Standard interpolators.
     * ============================================================================================
     */

    /**
     * The standard interpolator that should be used on every normal animation
     */
    public static final Interpolator STANDARD = new PathInterpolator(
            0.2f, 0f, 0f, 1f);

    /**
     * The standard accelerating interpolator that should be used on every regular movement of
     * content that is disappearing e.g. when moving off screen.
     */
    public static final Interpolator STANDARD_ACCELERATE = new PathInterpolator(
            0.3f, 0f, 1f, 1f);

    /**
     * The standard decelerating interpolator that should be used on every regular movement of
     * content that is appearing e.g. when coming from off screen.
     */
    public static final Interpolator STANDARD_DECELERATE = new PathInterpolator(
            0f, 0f, 0f, 1f);

    /*
     * ============================================================================================
     * Legacy
     * ============================================================================================
     */

    /**
     * The default legacy interpolator as defined in Material 1. Also known as FAST_OUT_SLOW_IN.
     */
    public static final Interpolator LEGACY = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /**
     * The default legacy accelerating interpolator as defined in Material 1.
     * Also known as FAST_OUT_LINEAR_IN.
     */
    public static final Interpolator LEGACY_ACCELERATE = new PathInterpolator(0.4f, 0f, 1f, 1f);

    /**
     * The default legacy decelerating interpolator as defined in Material 1.
     * Also known as LINEAR_OUT_SLOW_IN.
     */
    public static final Interpolator LEGACY_DECELERATE = new PathInterpolator(0f, 0f, 0.2f, 1f);

    /**
     * Linear interpolator. Often used if the interpolator is for different properties who need
     * different interpolations.
     */
    public static final Interpolator LINEAR = new LinearInterpolator();

    /*
    * ============================================================================================
    * Custom interpolators
    * ============================================================================================
    */

    public static final Interpolator FAST_OUT_SLOW_IN = LEGACY;
    public static final Interpolator FAST_OUT_LINEAR_IN = LEGACY_ACCELERATE;
    public static final Interpolator LINEAR_OUT_SLOW_IN = LEGACY_DECELERATE;

    /**
     * Like {@link #FAST_OUT_SLOW_IN}, but used in case the animation is played in reverse (i.e. t
     * goes from 1 to 0 instead of 0 to 1).
     */
    public static final Interpolator FAST_OUT_SLOW_IN_REVERSE =
            new PathInterpolator(0.8f, 0f, 0.6f, 1f);
    public static final Interpolator SLOW_OUT_LINEAR_IN = new PathInterpolator(0.8f, 0f, 1f, 1f);
    public static final Interpolator ALPHA_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    public static final Interpolator ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);
    public static final Interpolator ACCELERATE = new AccelerateInterpolator();
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
    public static final Interpolator CUSTOM_40_40 = new PathInterpolator(0.4f, 0f, 0.6f, 1f);
    public static final Interpolator ICON_OVERSHOT = new PathInterpolator(0.4f, 0f, 0.2f, 1.4f);
    public static final Interpolator ICON_OVERSHOT_LESS = new PathInterpolator(0.4f, 0f, 0.2f,
            1.1f);
    public static final Interpolator PANEL_CLOSE_ACCELERATED = new PathInterpolator(0.3f, 0, 0.5f,
            1);
    public static final Interpolator BOUNCE = new BounceInterpolator();
    /**
     * For state transitions on the control panel that lives in GlobalActions.
     */
    public static final Interpolator CONTROL_STATE = new PathInterpolator(0.4f, 0f, 0.2f,
            1.0f);

    /**
     * Interpolator to be used when animating a move based on a click. Pair with enough duration.
     */
    public static final Interpolator TOUCH_RESPONSE =
            new PathInterpolator(0.3f, 0f, 0.1f, 1f);

    /**
     * Like {@link #TOUCH_RESPONSE}, but used in case the animation is played in reverse (i.e. t
     * goes from 1 to 0 instead of 0 to 1).
     */
    public static final Interpolator TOUCH_RESPONSE_REVERSE =
            new PathInterpolator(0.9f, 0f, 0.7f, 1f);

    /*
     * ============================================================================================
     * Functions / Utilities
     * ============================================================================================
     */

    /**
     * Calculate the amount of overshoot using an exponential falloff function with desired
     * properties, where the overshoot smoothly transitions at the 1.0f boundary into the
     * overshoot, retaining its acceleration.
     *
     * @param progress a progress value going from 0 to 1
     * @param overshootAmount the amount > 0 of overshoot desired. A value of 0.1 means the max
     *                        value of the overall progress will be at 1.1.
     * @param overshootStart the point in (0,1] where the result should reach 1
     * @return the interpolated overshoot
     */
    public static float getOvershootInterpolation(float progress, float overshootAmount,
            float overshootStart) {
        if (overshootAmount == 0.0f || overshootStart == 0.0f) {
            throw new IllegalArgumentException("Invalid values for overshoot");
        }
        float b = MathUtils.log((overshootAmount + 1) / (overshootAmount)) / overshootStart;
        return MathUtils.max(0.0f,
                (float) (1.0f - Math.exp(-b * progress)) * (overshootAmount + 1.0f));
    }

    /**
     * Similar to {@link #getOvershootInterpolation(float, float, float)} but the overshoot
     * starts immediately here, instead of first having a section of non-overshooting
     *
     * @param progress a progress value going from 0 to 1
     */
    public static float getOvershootInterpolation(float progress) {
        return MathUtils.max(0.0f, (float) (1.0f - Math.exp(-4 * progress)));
    }

    // Create the default emphasized interpolator
    private static PathInterpolator createEmphasizedInterpolator() {
        Path path = new Path();
        // Doing the same as fast_out_extra_slow_in
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
}
