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
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * Various utility methods associated with theming.
 */
public class Themes {

    public static int getActivityThemeRes(Context context) {
        WallpaperColorInfo wallpaperColorInfo = WallpaperColorInfo.getInstance(context);
        boolean darkTheme;
        if (Utilities.ATLEAST_Q) {
            Configuration configuration = context.getResources().getConfiguration();
            int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            darkTheme = nightMode == Configuration.UI_MODE_NIGHT_YES;
        } else {
            darkTheme = wallpaperColorInfo.isDark();
        }

        if (darkTheme) {
            return wallpaperColorInfo.supportsDarkText() ?
                    R.style.AppTheme_Dark_DarkText : wallpaperColorInfo.isMainColorDark() ?
                            R.style.AppTheme_Dark_DarkMainColor : R.style.AppTheme_Dark;
        } else {
            return wallpaperColorInfo.supportsDarkText() ?
                    R.style.AppTheme_DarkText : wallpaperColorInfo.isMainColorDark() ?
                            R.style.AppTheme_DarkMainColor : R.style.AppTheme;
        }
    }

    public static String getDefaultBodyFont(Context context) {
        TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault,
                new int[]{android.R.attr.fontFamily});
        String value = ta.getString(0);
        ta.recycle();
        return value;
    }

    public static float getDialogCornerRadius(Context context) {
        return getDimension(context, android.R.attr.dialogCornerRadius,
                context.getResources().getDimension(R.dimen.default_dialog_corner_radius));
    }

    public static float getDimension(Context context, int attr, float defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float value = ta.getDimension(0, defaultValue);
        ta.recycle();
        return value;
    }

    public static int getColorAccent(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static boolean getAttrBoolean(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        boolean value = ta.getBoolean(0, false);
        ta.recycle();
        return value;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        Drawable value = ta.getDrawable(0);
        ta.recycle();
        return value;
    }

    public static int getAttrInteger(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int value = ta.getInteger(0, 0);
        ta.recycle();
        return value;
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

    /**
     * Changes a color matrix such that, when applied to srcColor, it produces dstColor.
     *
     * Note that values on the last column of target ColorMatrix can be negative, and may result in
     * negative values when applied on a color. Such negative values will be automatically shifted
     * up to 0 by the framework.
     *
     * @param srcColor The color to start from
     * @param dstColor The color to create by applying target on srcColor
     * @param target The ColorMatrix to transform the color
     */
    public static void setColorChangeOnMatrix(int srcColor, int dstColor, ColorMatrix target) {
        target.reset();
        target.getArray()[4] = Color.red(dstColor) - Color.red(srcColor);
        target.getArray()[9] = Color.green(dstColor) - Color.green(srcColor);
        target.getArray()[14] = Color.blue(dstColor) - Color.blue(srcColor);
        target.getArray()[19] = Color.alpha(dstColor) - Color.alpha(srcColor);
    }

    /**
     * Creates a map for attribute-name to value for all the values in {@param attrs} which can be
     * held in memory for later use.
     */
    public static SparseArray<TypedValue> createValueMap(Context context, AttributeSet attrSet,
            IntArray keysToIgnore) {
        int count = attrSet.getAttributeCount();
        IntArray attrNameArray = new IntArray(count);
        for (int i = 0; i < count; i++) {
            attrNameArray.add(attrSet.getAttributeNameResource(i));
        }
        attrNameArray.removeAllValues(keysToIgnore);

        int[] attrNames = attrNameArray.toArray();
        SparseArray<TypedValue> result = new SparseArray<>(attrNames.length);
        TypedArray ta = context.obtainStyledAttributes(attrSet, attrNames);
        for (int i = 0; i < attrNames.length; i++) {
            TypedValue tv = new TypedValue();
            ta.getValue(i, tv);
            result.put(attrNames[i], tv);
        }

        return result;
    }
}
