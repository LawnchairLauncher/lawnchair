/*
 * Copyright (C) 2015 The Android Open Source Project
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

package ch.deletescape.wallpaperpicker;

import android.content.res.Resources;
import android.graphics.Point;
import android.view.View;
import android.view.WindowManager;

/**
 * Utility methods for wallpaper management.
 */
public final class WallpaperUtils {

    public static final float WALLPAPER_SCREENS_SPAN = 2f;


    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    private static Point sDefaultWallpaperSize;

    public static Point getDefaultWallpaperSize(Resources res, WindowManager windowManager) {
        if (sDefaultWallpaperSize == null) {
            Point realSize = new Point();
            windowManager.getDefaultDisplay().getRealSize(realSize);
            int maxDim = Math.max(realSize.x, realSize.y);
            int minDim = Math.min(realSize.x, realSize.y);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            final int defaultWidth, defaultHeight;
            if (res.getConfiguration().smallestScreenWidthDp >= 720) {
                defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
                defaultHeight = maxDim;
            } else {
                defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
                defaultHeight = maxDim;
            }
            sDefaultWallpaperSize = new Point(defaultWidth, defaultHeight);
        }
        return sDefaultWallpaperSize;
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
