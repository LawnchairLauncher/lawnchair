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

package com.android.launcher3.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.view.WindowManager;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.Utilities;

/**
 * Utility methods for wallpaper management.
 */
public final class WallpaperUtils {

    public static final String WALLPAPER_WIDTH_KEY = "wallpaper.width";
    public static final String WALLPAPER_HEIGHT_KEY = "wallpaper.height";
    public static final float WALLPAPER_SCREENS_SPAN = 2f;

    public static void saveWallpaperDimensions(int width, int height, Activity activity) {
        if (Utilities.ATLEAST_KITKAT) {
            // From Kitkat onwards, ImageWallpaper does not care about the
            // desired width and desired height of the wallpaper.
            return;
        }
        String spKey = LauncherFiles.WALLPAPER_CROP_PREFERENCES_KEY;
        SharedPreferences sp = activity.getSharedPreferences(spKey, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = sp.edit();
        if (width != 0 && height != 0) {
            editor.putInt(WALLPAPER_WIDTH_KEY, width);
            editor.putInt(WALLPAPER_HEIGHT_KEY, height);
        } else {
            editor.remove(WALLPAPER_WIDTH_KEY);
            editor.remove(WALLPAPER_HEIGHT_KEY);
        }
        editor.commit();
        suggestWallpaperDimensionPreK(activity, true);
    }

    public static void suggestWallpaperDimensionPreK(
            Activity activity, boolean fallBackToDefaults) {
        final Point defaultWallpaperSize = getDefaultWallpaperSize(
                activity.getResources(), activity.getWindowManager());

        SharedPreferences sp = activity.getSharedPreferences(
                LauncherFiles.WALLPAPER_CROP_PREFERENCES_KEY, Context.MODE_MULTI_PROCESS);
        // If we have saved a wallpaper width/height, use that instead
        int width = sp.getInt(WALLPAPER_WIDTH_KEY, -1);
        int height = sp.getInt(WALLPAPER_HEIGHT_KEY, -1);

        if (width == -1 || height == -1) {
            if (!fallBackToDefaults) {
                return;
            } else {
                width = defaultWallpaperSize.x;
                height = defaultWallpaperSize.y;
            }
        }

        WallpaperManager wm = WallpaperManager.getInstance(activity);
        if (width != wm.getDesiredMinimumWidth() || height != wm.getDesiredMinimumHeight()) {
            wm.suggestDesiredDimensions(width, height);
        }
    }

    public static void suggestWallpaperDimension(Activity activity) {
        // Only live wallpapers care about desired size. Update the size to what launcher expects.
        final Point size = getDefaultWallpaperSize(
                activity.getResources(), activity.getWindowManager());
        WallpaperManager wm = WallpaperManager.getInstance(activity);
        if (size.x != wm.getDesiredMinimumWidth() || size.y != wm.getDesiredMinimumHeight()) {
            wm.suggestDesiredDimensions(size.x, size.y);
        }
    }

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

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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
}
