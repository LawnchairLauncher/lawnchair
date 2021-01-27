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

import android.animation.TimeInterpolator;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;

/**
 * Utility class for setting a property with or without animation
 */
public interface PropertySetter {

    PropertySetter NO_ANIM_PROPERTY_SETTER = new PropertySetter() { };

    /**
     * Sets the view alpha using the provided interpolator.
     * Unlike {@link #setFloat}, this also updates the visibility of the view as alpha changes
     * between zero and non-zero.
     */
    default void setViewAlpha(View view, float alpha, TimeInterpolator interpolator) {
        if (view != null) {
            view.setAlpha(alpha);
            AlphaUpdateListener.updateVisibility(view);
        }
    }

    /**
     * Updates the float property of the target using the provided interpolator
     */
    default <T> void setFloat(T target, FloatProperty<T> property, float value,
            TimeInterpolator interpolator) {
        property.setValue(target, value);
    }

    /**
     * Updates the int property of the target using the provided interpolator
     */
    default <T> void setInt(T target, IntProperty<T> property, int value,
            TimeInterpolator interpolator) {
        property.setValue(target, value);
    }
}
