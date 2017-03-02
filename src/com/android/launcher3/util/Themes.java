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

package com.android.launcher3.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.view.ContextThemeWrapper;

/**
 * Various utility methods associated with theming.
 */
public class Themes {

    public static int getColorAccent(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    public static int getColorPrimary(Context context, int theme) {
        return getAttrColor(new ContextThemeWrapper(context, theme), android.R.attr.colorPrimary);
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    /**
     * Returns the alpha corresponding to the theme attribute {@param attr}, in the range [0, 255].
     */
    public static int getAlpha(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float alpha = ta.getFloat(0, 0);
        ta.recycle();
        return (int) (255 * alpha + 0.5f);
    }

    /**
     * Scales a color matrix such that, when applied to color R G B A, it produces R' G' B' A' where
     * R' = r * R
     * G' = g * G
     * B' = b * B
     * A' = a * A
     *
     * The matrix will, for instance, turn white into r g b a, and black will remain black.
     *
     * @param color The color r g b a
     * @param target The ColorMatrix to scale
     */
    public static void setColorScaleOnMatrix(int color, ColorMatrix target) {
        target.setScale(Color.red(color) / 255f, Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }
}
