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

package com.android.launcher3.dynamicui;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.support.v7.graphics.Palette;
import android.util.Log;

import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

import java.io.IOException;

/**
 * Extracts colors from the wallpaper, and saves results to {@link LauncherProvider}.
 */
public class ColorExtractionService extends JobService {

    private static final String TAG = "ColorExtractionService";
    private static final boolean DEBUG = false;

    /** The fraction of the wallpaper to extract colors for use on the hotseat. */
    private static final float HOTSEAT_FRACTION = 1f / 4;

    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mWorkerThread = new HandlerThread("ColorExtractionService");
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWorkerThread.quit();
    }

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        if (DEBUG) Log.d(TAG, "onStartJob");
        mWorkerHandler.post(new Runnable() {
            @Override
            public void run() {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(
                        ColorExtractionService.this);
                int wallpaperId = ExtractionUtils.getWallpaperId(wallpaperManager);

                ExtractedColors extractedColors = new ExtractedColors();
                if (wallpaperManager.getWallpaperInfo() != null) {
                    // We can't extract colors from live wallpapers; always use the default color.
                    extractedColors.updateHotseatPalette(null);
                } else {
                    // We extract colors for the hotseat and status bar separately,
                    // since they only consider part of the wallpaper.
                    extractedColors.updateHotseatPalette(getHotseatPalette());

                    if (FeatureFlags.LIGHT_STATUS_BAR) {
                        extractedColors.updateStatusBarPalette(getStatusBarPalette());
                    }
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
                jobFinished(jobParameters, false /* needsReschedule */);
                if (DEBUG) Log.d(TAG, "job finished!");
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (DEBUG) Log.d(TAG, "onStopJob");
        mWorkerHandler.removeCallbacksAndMessages(null);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.N)
    private Palette getHotseatPalette() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        if (Utilities.ATLEAST_NOUGAT) {
            try (ParcelFileDescriptor fd = wallpaperManager
                    .getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
                BitmapRegionDecoder decoder = BitmapRegionDecoder
                        .newInstance(fd.getFileDescriptor(), false);
                int height = decoder.getHeight();
                Rect decodeRegion = new Rect(0, (int) (height * (1f - HOTSEAT_FRACTION)),
                        decoder.getWidth(), height);
                Bitmap bitmap = decoder.decodeRegion(decodeRegion, null);
                decoder.recycle();
                if (bitmap != null) {
                    return Palette.from(bitmap).clearFilters().generate();
                }
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "Fetching partial bitmap failed, trying old method", e);
            }
        }

        Bitmap wallpaper = ((BitmapDrawable) wallpaperManager.getDrawable()).getBitmap();
        return Palette.from(wallpaper)
                .setRegion(0, (int) (wallpaper.getHeight() * (1f - HOTSEAT_FRACTION)),
                        wallpaper.getWidth(), wallpaper.getHeight())
                .clearFilters()
                .generate();
    }

    @TargetApi(Build.VERSION_CODES.N)
    private Palette getStatusBarPalette() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        int statusBarHeight = getResources()
                .getDimensionPixelSize(R.dimen.status_bar_height);

        if (Utilities.ATLEAST_NOUGAT) {
            try (ParcelFileDescriptor fd = wallpaperManager
                    .getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
                BitmapRegionDecoder decoder = BitmapRegionDecoder
                        .newInstance(fd.getFileDescriptor(), false);
                Rect decodeRegion = new Rect(0, 0,
                        decoder.getWidth(), statusBarHeight);
                Bitmap bitmap = decoder.decodeRegion(decodeRegion, null);
                decoder.recycle();
                if (bitmap != null) {
                    return Palette.from(bitmap).clearFilters().generate();
                }
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "Fetching partial bitmap failed, trying old method", e);
            }
        }

        Bitmap wallpaper = ((BitmapDrawable) wallpaperManager.getDrawable()).getBitmap();
        return Palette.from(wallpaper)
                .setRegion(0, 0, wallpaper.getWidth(), statusBarHeight)
                .clearFilters()
                .generate();
    }
}
