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

import android.app.IntentService;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v7.graphics.Palette;

import ch.deletescape.lawnchair.LauncherProvider;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.R;

/**
 * Extracts colors from the wallpaper, and saves results to {@link LauncherProvider}.
 */
public class ColorExtractionService extends IntentService {

    /**
     * The fraction of the wallpaper to extract colors for use on the hotseat.
     */
    private static final float HOTSEAT_FRACTION = 0.5f;

    public ColorExtractionService() {
        super("ColorExtractionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        int wallpaperId = ExtractionUtils.getWallpaperId(wallpaperManager);
        ExtractedColors extractedColors = new ExtractedColors();
        PackageManager pm = getApplicationContext().getPackageManager();
        if (wallpaperManager.getWallpaperInfo() != null) {
            try {
                Bitmap wallpaper = ((BitmapDrawable) wallpaperManager.getWallpaperInfo().loadThumbnail(pm)).getBitmap();
                generatePaletteFromWallpaper(wallpaper, extractedColors);
            } catch (NullPointerException ignored) {
                extractedColors.updatePalette(null);
                extractedColors.updateHotseatPalette(null);
            }
        } else {
            Bitmap wallpaper = ((BitmapDrawable) wallpaperManager.getDrawable()).getBitmap();
            generatePaletteFromWallpaper(wallpaper, extractedColors);
        }

        // Save the extracted colors and wallpaper id to LauncherProvider.
        String colorsString = extractedColors.encodeAsString();
        Bundle extras = new Bundle();
        extras.putInt(LauncherSettings.Settings.EXTRA_WALLPAPER_ID, wallpaperId);
        extras.putString(LauncherSettings.Settings.EXTRA_EXTRACTED_COLORS, colorsString);
        getContentResolver().call(
                LauncherSettings.Settings.CONTENT_URI,
                LauncherSettings.Settings.METHOD_SET_EXTRACTED_COLORS_AND_WALLPAPER_ID,
                null, extras);
    }

    private void generatePaletteFromWallpaper(Bitmap wallpaper, ExtractedColors extractedColors) {
        Palette palette = Palette.from(wallpaper).generate();
        extractedColors.updatePalette(palette);
        // We extract colors for the hotseat and status bar separately,
        // since they only consider part of the wallpaper.
        Palette hotseatPalette = Palette.from(wallpaper)
                .setRegion(0, (int) (wallpaper.getHeight() * HOTSEAT_FRACTION),
                        wallpaper.getWidth(), wallpaper.getHeight())
                .clearFilters()
                .generate();
        extractedColors.updateHotseatPalette(hotseatPalette);

        int statusBarHeight = getResources()
                .getDimensionPixelSize(R.dimen.status_bar_height);
        Palette statusBarPalette = Palette.from(wallpaper)
                .setRegion(0, 0, wallpaper.getWidth(), statusBarHeight)
                .clearFilters()
                .generate();
        extractedColors.updateStatusBarPalette(statusBarPalette);

        int navigationBarHeight = getResources()
                .getDimensionPixelSize(R.dimen.navigation_bar_height);
        Palette navigationBarPalette = Palette.from(wallpaper)
                .setRegion(0, wallpaper.getHeight() - navigationBarHeight, wallpaper.getWidth(), wallpaper.getHeight())
                .clearFilters()
                .generate();
        extractedColors.updateNavigationBarPalette(navigationBarPalette);
    }
}
