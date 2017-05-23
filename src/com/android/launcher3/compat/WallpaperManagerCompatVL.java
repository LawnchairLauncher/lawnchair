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
package com.android.launcher3.compat;

import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.app.IntentService;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.Utilities;

import java.io.IOException;
import java.util.ArrayList;

public class WallpaperManagerCompatVL extends WallpaperManagerCompat {

    private static final String TAG = "WMCompatVL";

    private static final String VERSION_PREFIX = "1,";
    private static final String KEY_COLORS = "wallpaper_parsed_colors";
    private static final String EXTRA_RECEIVER = "receiver";

    private final ArrayList<OnColorsChangedListenerCompat> mListeners = new ArrayList<>();

    private final Context mContext;
    private WallpaperColorsCompat mColorsCompat;

    WallpaperManagerCompatVL(Context context) {
        mContext = context;

        String colors = prefs(mContext).getString(KEY_COLORS, "");
        int wallpaperId = -1;
        if (colors.startsWith(VERSION_PREFIX)) {
            Pair<Integer, WallpaperColorsCompat> storedValue = parseValue(colors);
            wallpaperId = storedValue.first;
            mColorsCompat = storedValue.second;
        }

        if (wallpaperId == -1 || wallpaperId != getWallpaperId(context)) {
            reloadColors();
        }
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reloadColors();
            }
        }, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
    }

    @Nullable
    @Override
    public WallpaperColorsCompat getWallpaperColors(int which) {
        return which == FLAG_SYSTEM ? mColorsCompat : null;
    }

    @Override
    public void addOnColorsChangedListener(OnColorsChangedListenerCompat listener) {
        mListeners.add(listener);
    }

    private void reloadColors() {
        ResultReceiver receiver = new ResultReceiver(new Handler()) {

            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                handleResult(resultData.getString(KEY_COLORS));
            }
        };
        mContext.startService(new Intent(mContext, ColorExtractionService.class)
                .putExtra(EXTRA_RECEIVER, receiver));
    }

    private void handleResult(String result) {
        prefs(mContext).edit().putString(KEY_COLORS, result).apply();
        mColorsCompat = parseValue(result).second;
        for (OnColorsChangedListenerCompat listener : mListeners) {
            listener.onColorsChanged(mColorsCompat, FLAG_SYSTEM);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.DEVICE_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    private static final int getWallpaperId(Context context) {
        if (!Utilities.ATLEAST_NOUGAT) {
            return -1;
        }
        return context.getSystemService(WallpaperManager.class).getWallpaperId(FLAG_SYSTEM);
    }

    /**
     * Parses the stored value and returns the wallpaper id and wallpaper colors.
     */
    private static Pair<Integer, WallpaperColorsCompat> parseValue(String value) {
        String[] parts = value.split(",");
        Integer wallpaperId = Integer.parseInt(parts[1]);
        if (parts.length == 2) {
            return Pair.create(wallpaperId, null);
        }

        SparseIntArray colors = new SparseIntArray((parts.length - 2) / 2);
        for (int i = 2; i < parts.length; i += 2) {
            colors.put(Integer.parseInt(parts[i]), Integer.parseInt(parts[i + 1]));
        }
        return Pair.create(wallpaperId, new WallpaperColorsCompat(colors, false));
    }

    /**
     * Intent service to handle color extraction
     */
    public static class ColorExtractionService extends IntentService {
        private static final int MAX_WALLPAPER_EXTRACTION_AREA = 112 * 112;

        public ColorExtractionService() {
            super("ColorExtractionService");
        }

        /**
         * Extracts the wallpaper colors and sends the result back through the receiver.
         */
        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            int wallpaperId = getWallpaperId(this);

            Bitmap bitmap = null;
            Drawable drawable = null;

            WallpaperManager wm = WallpaperManager.getInstance(this);
            WallpaperInfo info = wm.getWallpaperInfo();
            if (info != null) {
                // For live wallpaper, extract colors from thumbnail
                drawable = info.loadThumbnail(getPackageManager());
            } else {
                if (Utilities.ATLEAST_NOUGAT) {
                    try (ParcelFileDescriptor fd = wm.getWallpaperFile(FLAG_SYSTEM)) {
                        BitmapRegionDecoder decoder = BitmapRegionDecoder
                                .newInstance(fd.getFileDescriptor(), false);

                        int requestedArea = decoder.getWidth() * decoder.getHeight();
                        BitmapFactory.Options options = new BitmapFactory.Options();

                        if (requestedArea > MAX_WALLPAPER_EXTRACTION_AREA) {
                            double areaRatio =
                                    MAX_WALLPAPER_EXTRACTION_AREA / (double) requestedArea;
                            double nearestPowOf2 =
                                    Math.floor(Math.log(areaRatio) / (2 * Math.log(2)));
                            options.inSampleSize = (int) Math.pow(2, nearestPowOf2);
                        }
                        Rect region = new Rect(0, 0, decoder.getWidth(), decoder.getHeight());
                        bitmap = decoder.decodeRegion(region, options);
                        decoder.recycle();
                    } catch (IOException | NullPointerException e) {
                        Log.e(TAG, "Fetching partial bitmap failed, trying old method", e);
                    }
                }
                if (bitmap == null) {
                    drawable = wm.getDrawable();
                }
            }

            if (drawable != null) {
                // Calculate how big the bitmap needs to be.
                // This avoids unnecessary processing and allocation inside Palette.
                final int requestedArea = drawable.getIntrinsicWidth() *
                        drawable.getIntrinsicHeight();
                double scale = 1;
                if (requestedArea > MAX_WALLPAPER_EXTRACTION_AREA) {
                    scale = Math.sqrt(MAX_WALLPAPER_EXTRACTION_AREA / (double) requestedArea);
                }
                bitmap = Bitmap.createBitmap((int) (drawable.getIntrinsicWidth() * scale),
                        (int) (drawable.getIntrinsicHeight() * scale), Bitmap.Config.ARGB_8888);
                final Canvas bmpCanvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                drawable.draw(bmpCanvas);
            }

            String value = VERSION_PREFIX + wallpaperId;

            if (bitmap != null) {
                Palette palette = Palette.from(bitmap).generate();
                bitmap.recycle();

                StringBuilder builder = new StringBuilder(value);
                for (Palette.Swatch swatch : palette.getSwatches()) {
                    builder.append(',')
                            .append(swatch.getRgb())
                            .append(',')
                            .append(swatch.getPopulation());
                }
                value = builder.toString();
            }

            ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
            Bundle result = new Bundle();
            result.putString(KEY_COLORS, value);
            receiver.send(0, result);
        }
    }
}
