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

package ch.deletescape.lawnchair.dynamicui;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;

import java.util.List;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;

/**
 * Contains helper fields and methods related to extracting colors from the wallpaper.
 */
public class ExtractionUtils {

    private static final float MIN_CONTRAST_RATIO = 2f;

    /**
     * TODO: Find a way to use this pre-N
     * Extract colors in the :wallpaper-chooser process, if the wallpaper id has changed.
     * When the new colors are saved in the LauncherProvider,
     * Launcher will be notified in Launcher#onSettingsChanged(String, String).
     */
    public static void startColorExtractionServiceIfNecessary(final Context context) {
        // Run on a background thread, since the service is asynchronous anyway.
        Utilities.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                if (hasWallpaperIdChanged(context) || hasExtractionPreferencesChanged(context)) {
                    startColorExtractionService(context);
                }
            }
        });
    }

    /**
     * Starts the {@link ColorExtractionService} without checking the wallpaper id
     */
    public static void startColorExtractionService(Context context) {
        if(Utilities.hasStoragePermission(context)) {
            context.startService(new Intent(context, ColorExtractionService.class));
        }
    }

    private static boolean hasWallpaperIdChanged(Context context) {
        if (!Utilities.ATLEAST_NOUGAT) {
            // TODO: update an id in sharedprefs in onWallpaperChanged broadcast, and read it here.
            return false;
        }
        final IPreferenceProvider sharedPrefs = Utilities.getPrefs(context);
        int wallpaperId = getWallpaperId(WallpaperManager.getInstance(context));
        int savedWallpaperId = sharedPrefs.getWallpaperId();
        return wallpaperId != savedWallpaperId;
    }

    public static int getWallpaperId(WallpaperManager wallpaperManager) {
        if (Utilities.ATLEAST_NOUGAT) {
            return wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } else {
            return -1;
        }
    }

    public static boolean isSuperLight(Palette p) {
        return !isLegibleOnWallpaper(Color.WHITE, p.getSwatches());
    }

    public static boolean isSuperDark(Palette p) {
        return !isLegibleOnWallpaper(Color.BLACK, p.getSwatches());
    }

    /**
     * Given a color, returns true if that color is legible on
     * the given wallpaper color swatches, else returns false.
     */
    private static boolean isLegibleOnWallpaper(int color, List<Palette.Swatch> wallpaperSwatches) {
        int legiblePopulation = 0;
        int illegiblePopulation = 0;
        for (Palette.Swatch swatch : wallpaperSwatches) {
            if (isLegible(color, swatch.getRgb())) {
                legiblePopulation += swatch.getPopulation();
            } else {
                illegiblePopulation += swatch.getPopulation();
            }
        }
        return legiblePopulation > illegiblePopulation;
    }

    /**
     * @return Whether the foreground color is legible on the background color.
     */
    private static boolean isLegible(int foreground, int background) {
        background = ColorUtils.setAlphaComponent(background, 255);
        return ColorUtils.calculateContrast(foreground, background) >= MIN_CONTRAST_RATIO;
    }

    private static boolean hasExtractionPreferencesChanged(Context context) {
        IPreferenceProvider prefs = Utilities.getPrefs(context);
        boolean result = false;
        boolean hotseatColoringValue = prefs.getHotseatShouldUseExtractedColors();
        boolean lightStatusBarValue = prefs.getLightStatusBar();
        if (prefs.hotseatShouldUseExtractedColorsCache(!hotseatColoringValue) != hotseatColoringValue) {
            result = true;
            prefs.hotseatShouldUseExtractedColorsCache(hotseatColoringValue, false);
        }
        if (prefs.lightStatusBarKeyCache(!lightStatusBarValue) != lightStatusBarValue) {
            result = true;
            prefs.lightStatusBarKeyCache(lightStatusBarValue, false);
        }
        return result;
    }
}
