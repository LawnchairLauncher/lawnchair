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

import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;

/**
 * Contains colors based on the dominant color of an icon.
 */
public class IconPalette {

    public int backgroundColor;
    public int textColor;
    public int secondaryColor;

    public static IconPalette fromDominantColor(int dominantColor) {
        IconPalette palette = new IconPalette();
        palette.backgroundColor = getMutedColor(dominantColor);
        palette.textColor = getTextColorForBackground(palette.backgroundColor);
        palette.secondaryColor = getLowContrastColor(palette.backgroundColor);
        return palette;
    }

    private static int getMutedColor(int color) {
        int alpha = (int) (255 * 0.15f);
        return ColorUtils.compositeColors(ColorUtils.setAlphaComponent(color, alpha), Color.WHITE);
    }

    private static int getTextColorForBackground(int backgroundColor) {
        return getLighterOrDarkerVersionOfColor(backgroundColor, 3f);
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
