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

import static app.lawnchair.wallpaper.WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT;
import static app.lawnchair.wallpaper.WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.GraphicsUtils;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.ColorMode;
import app.lawnchair.theme.color.ColorTokens;
import app.lawnchair.wallpaper.WallpaperColorsCompat;
import app.lawnchair.wallpaper.WallpaperManagerCompat;
import app.lawnchair.ui.theme.ColorKt;

/**
 * Various utility methods associated with theming.
 */
@SuppressWarnings("NewApi")
public class Themes {

    public static final String KEY_THEMED_ICONS = "themed_icons";

    public static int getActivityThemeRes(Context context) {
        WallpaperColorsCompat colors = WallpaperManagerCompat.INSTANCE.get(context).getWallpaperColors();
        final int colorHints = colors != null ? colors.getColorHints() : 0;
        return getActivityThemeRes(context, colorHints);
    }

    public static Context createWidgetPreviewContext(Context context) {
        if (Utilities.isDarkTheme(context)) {
            return new ContextThemeWrapper(context, R.style.AppTheme_Dark);
        } else {
            return new ContextThemeWrapper(context, R.style.AppTheme_DarkText);
        }
    }

    public static int getActivityThemeRes(Context context, int wallpaperColorHints) {
        PreferenceManager2 prefs2 = PreferenceManager2.getInstance(context);
        ColorMode colorMode = PreferenceExtensionsKt.firstBlocking(prefs2.getWorkspaceTextColor());
        boolean supportsDarkText = (wallpaperColorHints & HINT_SUPPORTS_DARK_TEXT) != 0;
        boolean isMainColorDark = (wallpaperColorHints & HINT_SUPPORTS_DARK_THEME) != 0;

        if (Utilities.isDarkTheme(context)) {
            if (colorMode == ColorMode.LIGHT) {
                return R.style.AppTheme_Dark;
            } else if (colorMode == ColorMode.DARK) {
                return R.style.AppTheme_Dark_DarkText;
            } else {
                return supportsDarkText ? R.style.AppTheme_Dark_DarkText
                    : isMainColorDark ? R.style.AppTheme_Dark_DarkMainColor : R.style.AppTheme_Dark;
            }
        } else {
            if (colorMode == ColorMode.LIGHT) {
                return R.style.AppTheme;
            } else if (colorMode == ColorMode.DARK) {
                return R.style.AppTheme_DarkText;
            } else {
                return supportsDarkText ? R.style.AppTheme_DarkText
                    : isMainColorDark ? R.style.AppTheme_DarkMainColor : R.style.AppTheme;
            }
        }
    }

    /**
     * Returns true if workspace icon theming is enabled
     */
    public static boolean isThemedIconEnabled(Context context) {
        return FeatureFlags.ENABLE_THEMED_ICONS.get()
                && Utilities.getPrefs(context).getBoolean(KEY_THEMED_ICONS, false);
    }

    public static String getDefaultBodyFont(Context context) {
        TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault,
                new int[]{android.R.attr.fontFamily});
        String value = ta.getString(0);
        ta.recycle();
        return value;
    }

    public static float getDialogCornerRadius(Context context) {
        return context.getResources().getDimension(R.dimen.lawnchair_dialog_corner_radius);
    }

    public static float getDimension(Context context, int attr, float defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float value = ta.getDimension(0, defaultValue);
        ta.recycle();
        return value;
    }

    public static int getColorAccent(Context context) {
        return ColorKt.getAccentColor(context);
    }

    /** Returns the background color attribute. */
    public static int getColorBackground(Context context) {
        return ColorTokens.ColorBackground.resolveColor(context);
    }

    /** Returns the floating background color attribute. */
    public static int getColorBackgroundFloating(Context context) {
        return ColorTokens.ColorBackgroundFloating.resolveColor(context);
    }

    public static int getAttrColor(Context context, int attr) {
        return GraphicsUtils.getAttrColor(context, attr);
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
