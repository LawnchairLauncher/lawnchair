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
package com.android.systemui.shared.recents.utilities;

import android.animation.TypeEvaluator;
import android.graphics.RectF;

/**
 * This evaluator can be used to perform type interpolation between <code>RectF</code> values.
 */
public class RectFEvaluator implements TypeEvaluator<RectF> {

    private final RectF mRect = new RectF();

    /**
     * This function returns the result of linearly interpolating the start and
     * end Rect values, with <code>fraction</code> representing the proportion
     * between the start and end values. The calculation is a simple parametric
     * calculation on each of the separate components in the Rect objects
     * (left, top, right, and bottom).
     *
     * <p>The object returned will be the <code>reuseRect</code> passed into the constructor.</p>
     *
     * @param fraction   The fraction from the starting to the ending values
     * @param startValue The start Rect
     * @param endValue   The end Rect
     * @return A linear interpolation between the start and end values, given the
     *         <code>fraction</code> parameter.
     */
    @Override
    public RectF evaluate(float fraction, RectF startValue, RectF endValue) {
        float left = startValue.left + ((endValue.left - startValue.left) * fraction);
        float top = startValue.top + ((endValue.top - startValue.top) * fraction);
        float right = startValue.right + ((endValue.right - startValue.right) * fraction);
        float bottom = startValue.bottom + ((endValue.bottom - startValue.bottom) * fraction);
        mRect.set(left, top, right, bottom);
        return mRect;
    }
}
