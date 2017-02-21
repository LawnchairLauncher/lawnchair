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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.Property;

/**
 * An AnimatorListener that sets the given property to the given value at the end of the animation.
 */
public class PropertyResetListener<T, V> extends AnimatorListenerAdapter {

    private Property<T, V> mPropertyToReset;
    private V mResetToValue;

    public PropertyResetListener(Property<T, V> propertyToReset, V resetToValue) {
        mPropertyToReset = propertyToReset;
        mResetToValue = resetToValue;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        mPropertyToReset.set((T) ((ObjectAnimator) animation).getTarget(), mResetToValue);
    }
}
