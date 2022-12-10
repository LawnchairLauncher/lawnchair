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
package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;

/**
 * A mutable float which allows animating the value
 */
public class AnimatedFloat {

    public static final FloatProperty<AnimatedFloat> VALUE =
            new FloatProperty<AnimatedFloat>("value") {
                @Override
                public void setValue(AnimatedFloat obj, float v) {
                    obj.updateValue(v);
                }

                @Override
                public Float get(AnimatedFloat obj) {
                    return obj.value;
                }
            };

    private static final Runnable NO_OP = () -> { };

    private final Runnable mUpdateCallback;
    private ObjectAnimator mValueAnimator;
    // Only non-null when an animation is playing to this value.
    private Float mEndValue;

    public float value;

    public AnimatedFloat() {
        this(NO_OP);
    }

    public AnimatedFloat(Runnable updateCallback) {
        mUpdateCallback = updateCallback;
    }

    /**
     * Returns an animation from the current value to the given value.
     */
    public ObjectAnimator animateToValue(float end) {
        return animateToValue(value, end);
    }

    /**
     * Returns an animation from the given start value to the given end value.
     */
    public ObjectAnimator animateToValue(float start, float end) {
        cancelAnimation();
        mValueAnimator = ObjectAnimator.ofFloat(this, VALUE, start, end);
        mValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (mValueAnimator == animator) {
                    mEndValue = end;
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (mValueAnimator == animator) {
                    mValueAnimator = null;
                    mEndValue = null;
                }
            }
        });
        return mValueAnimator;
    }

    /**
     * Changes the value and calls the callback.
     * Note that the value can be directly accessed as well to avoid notifying the callback.
     */
    public void updateValue(float v) {
        if (Float.compare(v, value) != 0) {
            value = v;
            mUpdateCallback.run();
        }
    }

    /**
     * Cancels the animation.
     */
    public void cancelAnimation() {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
        }
    }

    /**
     * Ends the animation.
     */
    public void finishAnimation() {
        if (mValueAnimator != null && mValueAnimator.isRunning()) {
            mValueAnimator.end();
        }
    }

    public boolean isAnimating() {
        return mValueAnimator != null;
    }

    /**
     * Returns whether we are currently animating, and the animation's end value matches the given.
     */
    public boolean isAnimatingToValue(float endValue) {
        return isAnimating() && mEndValue != null && mEndValue == endValue;
    }

    /**
     * Returns whether we are currently not animating, and the animation's value matches the given.
     */
    public boolean isSettledOnValue(float endValue) {
        return !isAnimating() && value == endValue;
    }
}
