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

package com.android.launcher3.graphics;

import android.app.Notification;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * Contains colors based on the dominant color of an icon.
 */
public class IconPalette {

    private static final boolean DEBUG = false;
    private static final String TAG = "IconPalette";

    private static final float MIN_PRELOAD_COLOR_SATURATION = 0.2f;
    private static final float MIN_PRELOAD_COLOR_LIGHTNESS = 0.6f;

    private static IconPalette sBadgePalette;
    private static IconPalette sFolderBadgePalette;

    public final int dominantColor;
    public final int backgroundColor;
    public final ColorMatrixColorFilter backgroundColorMatrixFilter;
    public final ColorMatrixColorFilter saturatedBackgroundColorMatrixFilter;
    public final int textColor;
    public final int secondaryColor;

    private IconPalette(int color, boolean desaturateBackground) {
        dominantColor = color;
        backgroundColor = desaturateBackground ? getMutedColor(dominantColor, 0.87f) : dominantColor;
        ColorMatrix backgroundColorMatrix = new ColorMatrix();
        Themes.setColorScaleOnMatrix(backgroundColor, backgroundColorMatrix);
        backgroundColorMatrixFilter = new ColorMatrixColorFilter(backgroundColorMatrix);
        if (!desaturateBackground) {
            saturatedBackgroundColorMatrixFilter = backgroundColorMatrixFilter;
        } else {
            // Get slightly more saturated background color.
            Themes.setColorScaleOnMatrix(getMutedColor(dominantColor, 0.54f), backgroundColorMatrix);
            saturatedBackgroundColorMatrixFilter = new ColorMatrixColorFilter(backgroundColorMatrix);
        }
        textColor = getTextColorForBackground(backgroundColor);
        secondaryColor = getLowContrastColor(backgroundColor);
    }

    /**
     * Returns a color suitable for the progress bar color of preload icon.
     */
    public int getPreloadProgressColor(Context context) {
        int result = dominantColor;

        // Make sure that the dominant color has enough saturation to be visible properly.
        float[] hsv = new float[3];
        Color.colorToHSV(result, hsv);
        if (hsv[1] < MIN_PRELOAD_COLOR_SATURATION) {
            result = Themes.getColorAccent(context);
        } else {
            hsv[2] = Math.max(MIN_PRELOAD_COLOR_LIGHTNESS, hsv[2]);
            result = Color.HSVToColor(hsv);
        }
        return result;
    }

    public static IconPalette fromDominantColor(int dominantColor, boolean desaturateBackground) {
        return new IconPalette(dominantColor, desaturateBackground);
    }

    /**
     * Returns an IconPalette based on the badge_color in colors.xml.
     * If that color is Color.TRANSPARENT, then returns null instead.
     */
    public static @Nullable IconPalette getBadgePalette(Resources resources) {
        int badgeColor = resources.getColor(R.color.badge_color);
        if (badgeColor == Color.TRANSPARENT) {
            // Colors will be extracted per app icon, so a static palette won't work.
            return null;
        }
        if (sBadgePalette == null) {
            sBadgePalette = fromDominantColor(badgeColor, false);
        }
        return sBadgePalette;
    }

    /**
     * Returns an IconPalette based on the folder_badge_color in colors.xml.
     */
    public static @NonNull IconPalette getFolderBadgePalette(Resources resources) {
        if (sFolderBadgePalette == null) {
            int badgeColor = resources.getColor(R.color.folder_badge_color);
            sFolderBadgePalette = fromDominantColor(badgeColor, false);
        }
        return sFolderBadgePalette;
    }

    /**
     * Resolves a color such that it has enough contrast to be used as the
     * color of an icon or text on the given background color.
     *
     * @return a color of the same hue with enough contrast against the background.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    public static int resolveContrastColor(Context context, int color, int background) {
        final int resolvedColor = resolveColor(context, color);

        int contrastingColor = ensureTextContrast(resolvedColor, background);

        if (contrastingColor != resolvedColor) {
            if (DEBUG){
                Log.w(TAG, String.format(
                        "Enhanced contrast of notification for %s " +
                                "%s (over background) by changing #%s to %s",
                        context.getPackageName(),
                        contrastChange(resolvedColor, contrastingColor, background),
                        Integer.toHexString(resolvedColor), Integer.toHexString(contrastingColor)));
            }
        }
        return contrastingColor;
    }

    /**
     * Resolves {@param color} to an actual color if it is {@link Notification#COLOR_DEFAULT}
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private static int resolveColor(Context context, int color) {
        if (color == Notification.COLOR_DEFAULT) {
            return context.getColor(R.color.notification_icon_default_color);
        }
        return color;
    }

    /** For debugging. This was copied from com.android.internal.util.NotificationColorUtil. */
    private static String contrastChange(int colorOld, int colorNew, int bg) {
        return String.format("from %.2f:1 to %.2f:1",
                ColorUtils.calculateContrast(colorOld, bg),
                ColorUtils.calculateContrast(colorNew, bg));
    }

    /**
     * Finds a text color with sufficient contrast over bg that has the same hue as the original
     * color.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private static int ensureTextContrast(int color, int bg) {
        return findContrastColor(color, bg, 4.5);
    }
    /**
     * Finds a suitable color such that there's enough contrast.
     *
     * @param fg the color to start searching from.
     * @param bg the color to ensure contrast against.
     * @param minRatio the minimum contrast ratio required.
     * @return a color with the same hue as {@param color}, potentially darkened to meet the
     *          contrast ratio.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private static int findContrastColor(int fg, int bg, double minRatio) {
        if (ColorUtils.calculateContrast(fg, bg) >= minRatio) {
            return fg;
        }

        double[] lab = new double[3];
        ColorUtils.colorToLAB(bg, lab);
        double bgL = lab[0];
        ColorUtils.colorToLAB(fg, lab);
        double fgL = lab[0];
        boolean isBgDark = bgL < 50;

        double low = isBgDark ? fgL : 0, high = isBgDark ? 100 : fgL;
        final double a = lab[1], b = lab[2];
        for (int i = 0; i < 15 && high - low > 0.00001; i++) {
            final double l = (low + high) / 2;
            fg = ColorUtils.LABToColor(l, a, b);
            if (ColorUtils.calculateContrast(fg, bg) > minRatio) {
                if (isBgDark) high = l; else low = l;
            } else {
                if (isBgDark) low = l; else high = l;
            }
        }
        return ColorUtils.LABToColor(low, a, b);
    }

    private static int getMutedColor(int color, float whiteScrimAlpha) {
        int whiteScrim = ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * whiteScrimAlpha));
        return ColorUtils.compositeColors(whiteScrim, color);
    }

    private static int getTextColorForBackground(int backgroundColor) {
        return getLighterOrDarkerVersionOfColor(backgroundColor, 4.5f);
    }

    private static int getLowContrastColor(int color) {
        return getLighterOrDarkerVersionOfColor(color, 1.5f);
    }

    private static int getLighterOrDarkerVersionOfColor(int color, float contrastRatio) {
        int whiteMinAlpha = ColorUtils.calculateMinimumAlpha(Color.WHITE, color, contrastRatio);
        int blackMinAlpha = ColorUtils.calculateMinimumAlpha(Color.BLACK, color, contrastRatio);
        int translucentWhiteOrBlack;
        if (whiteMinAlpha >= 0) {
            translucentWhiteOrBlack = ColorUtils.setAlphaComponent(Color.WHITE, whiteMinAlpha);
        } else if (blackMinAlpha >= 0) {
            translucentWhiteOrBlack = ColorUtils.setAlphaComponent(Color.BLACK, blackMinAlpha);
        } else {
            translucentWhiteOrBlack = Color.WHITE;
        }
        return ColorUtils.compositeColors(translucentWhiteOrBlack, color);
    }
}
