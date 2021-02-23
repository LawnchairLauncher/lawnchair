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
 * limitations under the License
 */

package com.android.systemutils;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Class to process wallpaper colors and generate a tonal palette based on them.
 */
@RequiresApi(api = VERSION_CODES.O_MR1)
public class ColorExtractor implements WallpaperManager.OnColorsChangedListener {

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_DARK = 1;
    public static final int TYPE_EXTRA_DARK = 2;
    private static final int[] sGradientTypes = new int[]{TYPE_NORMAL, TYPE_DARK, TYPE_EXTRA_DARK};

    private static final String TAG = "ColorExtractor";
    private static final boolean DEBUG = false;

    protected final SparseArray<GradientColors[]> mGradientColors;
    private final ArrayList<WeakReference<OnColorsChangedListener>> mOnColorsChangedListeners;
    private final Context mContext;
    private final ExtractionType mExtractionType;
    protected WallpaperColors mSystemColors;
    protected WallpaperColors mLockColors;

    public ColorExtractor(Context context) {
        this(context, new Tonal(context), true /* immediately */,
                context.getSystemService(WallpaperManager.class));
    }

    public ColorExtractor(Context context, ExtractionType extractionType, boolean immediately,
            WallpaperManager wallpaperManager) {
        mContext = context;
        mExtractionType = extractionType;

        mGradientColors = new SparseArray<>();
        for (int which : new int[]{WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM}) {
            GradientColors[] colors = new GradientColors[sGradientTypes.length];
            mGradientColors.append(which, colors);
            for (int type : sGradientTypes) {
                colors[type] = new GradientColors();
            }
        }

        mOnColorsChangedListeners = new ArrayList<>();
        if (wallpaperManager.isWallpaperSupported()) {
            wallpaperManager.addOnColorsChangedListener(this, null /* handler */);
            initExtractColors(wallpaperManager, immediately);
        }
    }

    private void initExtractColors(WallpaperManager wallpaperManager, boolean immediately) {
        if (immediately) {
            mSystemColors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            mLockColors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_LOCK);
            extractWallpaperColors();
        } else {
            new LoadWallpaperColors().executeOnExecutor(
                    AsyncTask.THREAD_POOL_EXECUTOR, wallpaperManager);
        }
    }

    private class LoadWallpaperColors extends AsyncTask<WallpaperManager, Void, Void> {

        private WallpaperColors mSystemColors;
        private WallpaperColors mLockColors;

        @Override
        protected Void doInBackground(WallpaperManager... params) {
            mSystemColors = params[0].getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            mLockColors = params[0].getWallpaperColors(WallpaperManager.FLAG_LOCK);
            return null;
        }

        @Override
        protected void onPostExecute(Void b) {
            ColorExtractor.this.mSystemColors = mSystemColors;
            ColorExtractor.this.mLockColors = mLockColors;
            extractWallpaperColors();
            triggerColorsChanged(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        }
    }

    protected void extractWallpaperColors() {
        GradientColors[] systemColors = mGradientColors.get(WallpaperManager.FLAG_SYSTEM);
        GradientColors[] lockColors = mGradientColors.get(WallpaperManager.FLAG_LOCK);
        extractInto(mSystemColors,
                systemColors[TYPE_NORMAL],
                systemColors[TYPE_DARK],
                systemColors[TYPE_EXTRA_DARK]);
        extractInto(mLockColors,
                lockColors[TYPE_NORMAL],
                lockColors[TYPE_DARK],
                lockColors[TYPE_EXTRA_DARK]);
    }

    /**
     * Retrieve gradient colors for a specific wallpaper.
     *
     * @param which FLAG_LOCK or FLAG_SYSTEM
     * @return colors
     */
    @NonNull
    public GradientColors getColors(int which) {
        return getColors(which, TYPE_DARK);
    }

    /**
     * Get current gradient colors for one of the possible gradient types
     *
     * @param which FLAG_LOCK or FLAG_SYSTEM
     * @param type TYPE_NORMAL, TYPE_DARK or TYPE_EXTRA_DARK
     * @return colors
     */
    @NonNull
    public GradientColors getColors(int which, int type) {
        if (type != TYPE_NORMAL && type != TYPE_DARK && type != TYPE_EXTRA_DARK) {
            throw new IllegalArgumentException(
                    "type should be TYPE_NORMAL, TYPE_DARK or TYPE_EXTRA_DARK");
        }
        if (which != WallpaperManager.FLAG_LOCK && which != WallpaperManager.FLAG_SYSTEM) {
            throw new IllegalArgumentException("which should be FLAG_SYSTEM or FLAG_NORMAL");
        }
        return mGradientColors.get(which)[type];
    }

    /**
     * Get the last available WallpaperColors without forcing new extraction.
     *
     * @param which FLAG_LOCK or FLAG_SYSTEM
     * @return Last cached colors
     */
    @Nullable
    public WallpaperColors getWallpaperColors(int which) {
        if (which == WallpaperManager.FLAG_LOCK) {
            return mLockColors;
        } else if (which == WallpaperManager.FLAG_SYSTEM) {
            return mSystemColors;
        } else {
            throw new IllegalArgumentException("Invalid value for which: " + which);
        }
    }

    @Override
    public void onColorsChanged(WallpaperColors colors, int which) {
        if (DEBUG) {
            Log.d(TAG, "New wallpaper colors for " + which + ": " + colors);
        }
        boolean changed = false;
        if ((which & WallpaperManager.FLAG_LOCK) != 0) {
            mLockColors = colors;
            GradientColors[] lockColors = mGradientColors.get(WallpaperManager.FLAG_LOCK);
            extractInto(colors, lockColors[TYPE_NORMAL], lockColors[TYPE_DARK],
                    lockColors[TYPE_EXTRA_DARK]);
            changed = true;
        }
        if ((which & WallpaperManager.FLAG_SYSTEM) != 0) {
            mSystemColors = colors;
            GradientColors[] systemColors = mGradientColors.get(WallpaperManager.FLAG_SYSTEM);
            extractInto(colors, systemColors[TYPE_NORMAL], systemColors[TYPE_DARK],
                    systemColors[TYPE_EXTRA_DARK]);
            changed = true;
        }

        if (changed) {
            triggerColorsChanged(which);
        }
    }

    protected void triggerColorsChanged(int which) {
        ArrayList<WeakReference<OnColorsChangedListener>> references =
                new ArrayList<>(mOnColorsChangedListeners);
        final int size = references.size();
        for (int i = 0; i < size; i++) {
            final WeakReference<OnColorsChangedListener> weakReference = references.get(i);
            final OnColorsChangedListener listener = weakReference.get();
            if (listener == null) {
                mOnColorsChangedListeners.remove(weakReference);
            } else {
                listener.onColorsChanged(this, which);
            }
        }
    }

    private void extractInto(WallpaperColors inWallpaperColors,
            GradientColors outGradientColorsNormal, GradientColors outGradientColorsDark,
            GradientColors outGradientColorsExtraDark) {
        mExtractionType.extractInto(inWallpaperColors, outGradientColorsNormal,
                outGradientColorsDark, outGradientColorsExtraDark);
    }

    @RequiresApi(api = VERSION_CODES.O_MR1)
    public void destroy() {
        WallpaperManager wallpaperManager = mContext.getSystemService(WallpaperManager.class);
        if (wallpaperManager != null) {
            wallpaperManager.removeOnColorsChangedListener(this);
        }
    }

    public void addOnColorsChangedListener(@NonNull OnColorsChangedListener listener) {
        mOnColorsChangedListeners.add(new WeakReference<>(listener));
    }

    public void removeOnColorsChangedListener(@NonNull OnColorsChangedListener listener) {
        ArrayList<WeakReference<OnColorsChangedListener>> references =
                new ArrayList<>(mOnColorsChangedListeners);
        final int size = references.size();
        for (int i = 0; i < size; i++) {
            final WeakReference<OnColorsChangedListener> weakReference = references.get(i);
            if (weakReference.get() == listener) {
                mOnColorsChangedListeners.remove(weakReference);
                break;
            }
        }
    }

    public static class GradientColors {

        private int mMainColor;
        private int mSecondaryColor;
        private int[] mColorPalette;
        private boolean mSupportsDarkText;

        public void setMainColor(int mainColor) {
            mMainColor = mainColor;
        }

        public void setSecondaryColor(int secondaryColor) {
            mSecondaryColor = secondaryColor;
        }

        public void setColorPalette(int[] colorPalette) {
            mColorPalette = colorPalette;
        }

        public void setSupportsDarkText(boolean supportsDarkText) {
            mSupportsDarkText = supportsDarkText;
        }

        public void set(GradientColors other) {
            mMainColor = other.mMainColor;
            mSecondaryColor = other.mSecondaryColor;
            mColorPalette = other.mColorPalette;
            mSupportsDarkText = other.mSupportsDarkText;
        }

        public int getMainColor() {
            return mMainColor;
        }

        public int getSecondaryColor() {
            return mSecondaryColor;
        }

        public int[] getColorPalette() {
            return mColorPalette;
        }

        public boolean supportsDarkText() {
            return mSupportsDarkText;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            GradientColors other = (GradientColors) o;
            return other.mMainColor == mMainColor &&
                    other.mSecondaryColor == mSecondaryColor &&
                    other.mSupportsDarkText == mSupportsDarkText;
        }

        @Override
        public int hashCode() {
            int code = mMainColor;
            code = 31 * code + mSecondaryColor;
            code = 31 * code + (mSupportsDarkText ? 0 : 1);
            return code;
        }

        @Override
        public String toString() {
            return "GradientColors(" + Integer.toHexString(mMainColor) + ", "
                    + Integer.toHexString(mSecondaryColor) + ")";
        }
    }

    public interface OnColorsChangedListener {

        void onColorsChanged(ColorExtractor colorExtractor, int which);
    }
}