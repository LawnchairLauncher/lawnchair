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
package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.FloatProperty;

import com.android.launcher3.util.DefaultDisplay;

import androidx.annotation.FloatRange;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Utility class to build an object animator which follows the same path as a spring animation for
 * an underdamped spring.
 */
public class SpringAnimationBuilder<T> extends FloatProperty<T> {

    private final T mTarget;
    private final FloatProperty<T> mProperty;

    private float mStartValue;
    private float mEndValue;
    private float mVelocity = 0;

    private float mStiffness = SpringForce.STIFFNESS_MEDIUM;
    private float mDampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
    private float mMinVisibleChange = 1;

    // Multiplier to the min visible change value for value threshold
    private static final float THRESHOLD_MULTIPLIER = 0.65f;

    /**
     * The spring equation is given as
     *   x = e^(-beta*t/2) * (a cos(gamma * t) + b sin(gamma * t)
     *   v = e^(-beta*t/2) * ((2 * a * gamma + beta * b) * sin(gamma * t)
     *                  + (a * beta - 2 * b * gamma) * cos(gamma * t)) / 2
     *
     *   a = x(0)
     *   b = beta * x(0) / (2 * gamma) + v(0) / gamma
     */
    private double beta;
    private double gamma;

    private double a, b;
    private double va, vb;

    // Threshold for velocity and value to determine when it's reasonable to assume that the spring
    // is approximately at rest.
    private double mValueThreshold;
    private double mVelocityThreshold;

    private float mCurrentTime = 0;

    public SpringAnimationBuilder(T target, FloatProperty<T> property) {
        super("dynamic-spring-property");
        mTarget = target;
        mProperty = property;

        mStartValue = mProperty.get(target);
    }

    public SpringAnimationBuilder<T> setEndValue(float value) {
        mEndValue = value;
        return this;
    }

    public SpringAnimationBuilder<T> setStartValue(float value) {
        mStartValue = value;
        return this;
    }

    public SpringAnimationBuilder<T> setValues(float... values) {
        if (values.length > 1) {
            mStartValue = values[0];
            mEndValue = values[values.length - 1];
        } else {
            mEndValue = values[0];
        }
        return this;
    }

    public SpringAnimationBuilder<T> setStiffness(
            @FloatRange(from = 0.0, fromInclusive = false) float stiffness) {
        if (stiffness <= 0) {
            throw new IllegalArgumentException("Spring stiffness constant must be positive.");
        }
        mStiffness = stiffness;
        return this;
    }

    public SpringAnimationBuilder<T> setDampingRatio(
            @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
                    float dampingRatio) {
        if (dampingRatio <= 0 || dampingRatio >= 1) {
            throw new IllegalArgumentException("Damping ratio must be between 0 and 1");
        }
        mDampingRatio = dampingRatio;
        return this;
    }

    public SpringAnimationBuilder<T> setMinimumVisibleChange(
            @FloatRange(from = 0.0, fromInclusive = false) float minimumVisibleChange) {
        if (minimumVisibleChange <= 0) {
            throw new IllegalArgumentException("Minimum visible change must be positive.");
        }
        mMinVisibleChange = minimumVisibleChange;
        return this;
    }

    public SpringAnimationBuilder<T> setStartVelocity(float startVelocity) {
        mVelocity = startVelocity;
        return this;
    }

    @Override
    public void setValue(T object, float time) {
        mCurrentTime = time;
        mProperty.setValue(
                object, (float) (exponentialComponent(time) * cosSinX(time)) + mEndValue);
    }

    @Override
    public Float get(T t) {
        return mCurrentTime;
    }

    public ObjectAnimator build(Context context) {
        int singleFrameMs = DefaultDisplay.getSingleFrameMs(context);
        double naturalFreq = Math.sqrt(mStiffness);
        double dampedFreq = naturalFreq * Math.sqrt(1 - mDampingRatio * mDampingRatio);

        // All the calculations assume the stable position to be 0, shift the values accordingly.
        beta = 2 * mDampingRatio * naturalFreq;
        gamma = dampedFreq;
        a =  mStartValue - mEndValue;
        b = beta * a / (2 * gamma) + mVelocity / gamma;

        va = a * beta / 2 - b * gamma;
        vb = a * gamma + beta * b / 2;

        mValueThreshold = mMinVisibleChange * THRESHOLD_MULTIPLIER;

        // This multiplier is used to calculate the velocity threshold given a certain value
        // threshold. The idea is that if it takes >= 1 frame to move the value threshold amount,
        // then the velocity is a reasonable threshold.
        mVelocityThreshold = mValueThreshold * 1000.0 / singleFrameMs;

        // Find the duration (in seconds) for the spring to reach equilibrium.
        // equilibrium is reached when x = 0
        double duration = Math.atan2(-a, b) / gamma;

        // Keep moving ahead until the velocity reaches equilibrium.
        double piByG = Math.PI / gamma;
        while (duration < 0 || Math.abs(exponentialComponent(duration) * cosSinV(duration))
                >= mVelocityThreshold) {
            duration += piByG;
        }

        // Find the shortest time
        double edgeTime = Math.max(0, duration - piByG / 2);
        double minDiff = singleFrameMs / 2000.0;    // Half frame time in seconds

        do {
            if ((duration - edgeTime) < minDiff) {
                break;
            }
            double mid = (edgeTime + duration) / 2;
            if (isAtEquilibrium(mid)) {
                duration = mid;
            } else {
                edgeTime = mid;
            }
        } while (true);


        long durationMs = (long) (1000.0 * duration);
        ObjectAnimator animator = ObjectAnimator.ofFloat(mTarget, this, 0, (float) duration);
        animator.setDuration(durationMs).setInterpolator(Interpolators.LINEAR);
        animator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mProperty.setValue(mTarget, mEndValue);
            }
        });
        return animator;
    }

    private boolean isAtEquilibrium(double t) {
        double ec = exponentialComponent(t);

        if (Math.abs(ec * cosSinX(t)) >= mValueThreshold) {
            return false;
        }
        return Math.abs(ec * cosSinV(t)) < mVelocityThreshold;
    }

    private double exponentialComponent(double t) {
        return Math.pow(Math.E, - beta * t / 2);
    }

    private double cosSinX(double t) {
        return cosSin(t, a, b);
    }

    private double cosSinV(double t) {
        return cosSin(t, va, vb);
    }

    private double cosSin(double t, double cosFactor, double sinFactor) {
        double angle = t * gamma;
        return cosFactor * Math.cos(angle) + sinFactor * Math.sin(angle);
    }
}
