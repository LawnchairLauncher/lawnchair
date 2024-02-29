/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.util;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

import com.android.launcher3.Utilities;

import java.util.ArrayList;

/**
 * Utility class to update multiple values with different interpolators and durations during
 * the same animation.
 */
public abstract class MultiValueUpdateListener implements ValueAnimator.AnimatorUpdateListener {

    private final ArrayList<FloatProp> mAllProperties = new ArrayList<>();

    @Override
    public final void onAnimationUpdate(ValueAnimator animator) {
        final float percent = animator.getAnimatedFraction();

        for (int i = mAllProperties.size() - 1; i >= 0; i--) {
            FloatProp prop = mAllProperties.get(i);
            float interpolatedPercent = prop.mInterpolator.getInterpolation(percent);
            prop.value = Utilities.mapRange(interpolatedPercent, prop.mStart, prop.mEnd);
        }
        onUpdate(percent, false /* initOnly */);
    }

    /**
     * @param percent The total animation progress.
     * @param initOnly When true, only does enough work to initialize the animation.
     */
    public abstract void onUpdate(float percent, boolean initOnly);

    public final class FloatProp {

        public float value;

        private final float mStart;
        private final float mEnd;
        private final Interpolator mInterpolator;

        public FloatProp(float start, float end, Interpolator i) {
            value = mStart = start;
            mEnd = end;
            mInterpolator = i;
            mAllProperties.add(this);
        }

        /**
         * Gets the start value.
         */
        public float getStartValue() {
            return mStart;
        }
    }
}
