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

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static com.android.launcher3.Utilities.getDevicePrefs;

public class WallpaperManagerCompatVL extends WallpaperManagerCompat {

    private static final String TAG = "WMCompatVL";

    private static final String VERSION_PREFIX = "1,";
    private static final String KEY_COLORS = "wallpaper_parsed_colors";
    private static final String ACTION_EXTRACTION_COMPLETE =
            "com.android.launcher3.compat.WallpaperManagerCompatVL.EXTRACTION_COMPLETE";

    private final ArrayList<OnColorsChangedListenerCompat> mListeners = new ArrayList<>();

    private final Context mContext;
    private WallpaperColorsCompat mColorsCompat;

    WallpaperManagerCompatVL(Context context) {
        mContext = context;

        String colors = getDevicePrefs(mContext).getString(KEY_COLORS, "");
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

        // Register a receiver for results
        String permission = null;
        // Find a permission which only we can use.
        try {
            for (PermissionInfo info : context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_PERMISSIONS).permissions) {
                if ((info.protectionLevel & PermissionInfo.PROTECTION_SIGNATURE) != 0) {
                    permission = info.name;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Something went wrong. ignore
            Log.d(TAG, "Unable to get permission info", e);
        }
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleResult(intent.getStringExtra(KEY_COLORS));
            }
        }, new IntentFilter(ACTION_EXTRACTION_COMPLETE), permission, new Handler());
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
        JobInfo job = new JobInfo.Builder(Utilities.WALLPAPER_COMPAT_JOB_ID,
                new ComponentName(mContext, ColorExtractionService.class))
                .setMinimumLatency(0).build();
        ((JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(job);
    }

    private void handleResult(String result) {
        getDevicePrefs(mContext).edit().putString(KEY_COLORS, result).apply();
        mColorsCompat = parseValue(result).second;
        for (OnColorsChangedListenerCompat listener : mListeners) {
            listener.onColorsChanged(mColorsCompat, FLAG_SYSTEM);
        }
    }

    private static final int getWallpaperId(Context context) {
        if (!Utilities.ATLEAST_NOUGAT) {
            Drawable wallpaper = WallpaperManager.getInstance(context).getDrawable();
            if (wallpaper != null) {
                Bitmap bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bm);
                wallpaper.setBounds(0, 0, cv.getWidth(), cv.getHeight());
                wallpaper.draw(cv);
                int c = bm.getPixel(0, 0);
                bm.recycle();
                return c;
            }
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
            // There is no wallpaper color info present, eg when live wallpaper has no preview.
            return Pair.create(wallpaperId, null);
        }

        int hints = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        int primary = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
        int secondary = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int tertiary = parts.length > 5 ? Integer.parseInt(parts[5]) : 0;

        return Pair.create(wallpaperId, new WallpaperColorsCompat(primary, secondary, tertiary,
                hints));
    }

    /**
     * Intent service to handle color extraction
     */
    public static class ColorExtractionService extends JobService implements Runnable {
        private static final int MAX_WALLPAPER_EXTRACTION_AREA = 112 * 112;

        // Decides when dark theme is optimal for this wallpaper
        private static final float DARK_THEME_MEAN_LUMINANCE = 0.25f;
        // Minimum mean luminosity that an image needs to have to support dark text
        private static final float BRIGHT_IMAGE_MEAN_LUMINANCE = 0.75f;
        // We also check if the image has dark pixels in it,
        // to avoid bright images with some dark spots.
        private static final float DARK_PIXEL_LUMINANCE = 0.45f;
        private static final float MAX_DARK_AREA = 0.05f;

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
            mWorkerHandler.post(this);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters jobParameters) {
            mWorkerHandler.removeCallbacksAndMessages(null);
            return true;
        }

        /**
         * Extracts the wallpaper colors and sends the result back through the receiver.
         */
        @Override
        public void run() {
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
                                    (double) requestedArea / MAX_WALLPAPER_EXTRACTION_AREA;
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
                int hints = calculateDarkHints(bitmap);
                bitmap.recycle();

                StringBuilder builder = new StringBuilder(value);
                List<Pair<Integer,Integer>> colorsToOccurrences = new ArrayList<>();
                for (Palette.Swatch swatch : palette.getSwatches()) {
                    colorsToOccurrences.add(new Pair(swatch.getRgb(), swatch.getPopulation()));
                }

                Collections.sort(colorsToOccurrences, new Comparator<Pair<Integer, Integer>>() {
                    @Override
                    public int compare(Pair<Integer, Integer> a, Pair<Integer, Integer> b) {
                        return b.second - a.second;
                    }
                });

                builder.append(',').append(hints);
                for (int i=0; i < Math.min(3, colorsToOccurrences.size()); i++) {
                    builder.append(',').append(colorsToOccurrences.get(i).first);
                }

                value = builder.toString();
            }

            // Send the result
            sendBroadcast(new Intent(ACTION_EXTRACTION_COMPLETE)
                    .setPackage(getPackageName())
                    .putExtra(KEY_COLORS, value));
        }

        /**
         * Checks if image is bright and clean enough to support light text.
         *
         * @param source What to read.
         * @return Whether image supports dark text or not.
         */
        private static int calculateDarkHints(Bitmap source) {
            if (source == null) {
                return 0;
            }
            int[] pixels = new int[source.getWidth() * source.getHeight()];
            double totalLuminance = 0;
            final int maxDarkPixels = (int) (pixels.length * MAX_DARK_AREA);
            int darkPixels = 0;
            source.getPixels(pixels, 0 /* offset */, source.getWidth(), 0 /* x */, 0 /* y */,
                    source.getWidth(), source.getHeight());
            // This bitmap was already resized to fit the maximum allowed area.
            // Let's just loop through the pixels, no sweat!
            float[] tmpHsl = new float[3];
            for (int i = 0; i < pixels.length; i++) {
                ColorUtils.colorToHSL(pixels[i], tmpHsl);
                final float luminance = tmpHsl[2];
                final int alpha = Color.alpha(pixels[i]);
                // Make sure we don't have a dark pixel mass that will
                // make text illegible.
                if (luminance < DARK_PIXEL_LUMINANCE && alpha != 0) {
                    darkPixels++;
                }
                totalLuminance += luminance;
            }
            int hints = 0;
            double meanLuminance = totalLuminance / pixels.length;
            if (meanLuminance > BRIGHT_IMAGE_MEAN_LUMINANCE && darkPixels < maxDarkPixels) {
                hints |= WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT;
            }
            if (meanLuminance < DARK_THEME_MEAN_LUMINANCE) {
                hints |= WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME;
            }
            return hints;
        }
    }
}
