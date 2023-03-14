/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

/**
 * Utility class for setting a property with or without animation
 */
public abstract class PropertySetter {

    public static final PropertySetter NO_ANIM_PROPERTY_SETTER = new PropertySetter() {

        @Override
        public void add(Animator animatorSet) {
            animatorSet.setDuration(0);
            animatorSet.start();
            animatorSet.end();
        }
    };

    protected static final AnimatorSet NO_OP = new AnimatorSet();

    /**
     * Sets the view alpha using the provided interpolator.
     * Unlike {@link #setFloat}, this also updates the visibility of the view as alpha changes
     * between zero and non-zero.
     */
    @NonNull
    public Animator setViewAlpha(View view, float alpha, TimeInterpolator interpolator) {
        if (view != null) {
            view.setAlpha(alpha);
            AlphaUpdateListener.updateVisibility(view);
        }
        return NO_OP;
    }

    /**
     * Sets the background color of the provided view using the provided interpolator.
     */
    @NonNull
    public Animator setViewBackgroundColor(View view, int color, TimeInterpolator interpolator) {
        if (view != null) {
            view.setBackgroundColor(color);
        }
        return NO_OP;
    }

    /**
     * Updates the float property of the target using the provided interpolator
     */
    @NonNull
    public <T> Animator setFloat(T target, FloatProperty<T> property, float value,
            TimeInterpolator interpolator) {
        property.setValue(target, value);
        return NO_OP;
    }

    /**
     * Updates the int property of the target using the provided interpolator
     */
    @NonNull
    public <T> Animator setInt(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        property.setValue(target, value);
        return NO_OP;
    }

    /**
     * Updates a color property of the target using the provided interpolator
     */
    @NonNull
    public <T> Animator setColor(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        property.setValue(target, value);
        return NO_OP;
    }

    /**
     * Runs the animation as part of setting the property
     */
    public abstract void add(Animator animatorSet);

    /**
     * Add a listener of receiving the success/failure callback in the end.
     */
    public void addEndListener(Consumer<Boolean> listener) {
        listener.accept(true);
    }

    /**
     * Creates and returns the AnimatorSet that can be run to apply the properties
     */
    @NonNull
    public AnimatorSet buildAnim() {
        return NO_OP;
    }
}
