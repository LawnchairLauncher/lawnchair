package com.android.launcher3.dynamicui.colorextraction;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Parcelable;
import android.util.Log;

import com.android.launcher3.dynamicui.colorextraction.types.ExtractionType;
import com.android.launcher3.dynamicui.colorextraction.types.Tonal;

import java.lang.reflect.Method;


/**
 * Class to process wallpaper colors and generate a tonal palette based on them.
 *
 * TODO remove this class if available by platform
 */
public class ColorExtractor {
    private static final String TAG = "ColorExtractor";
    private static final int FALLBACK_COLOR = Color.WHITE;

    private int mMainFallbackColor = FALLBACK_COLOR;
    private int mSecondaryFallbackColor = FALLBACK_COLOR;
    private final GradientColors mSystemColors;
    private final GradientColors mLockColors;
    private final Context mContext;
    private final ExtractionType mExtractionType;

    public ColorExtractor(Context context) {
        mContext = context;
        mSystemColors = new GradientColors();
        mLockColors = new GradientColors();
        mExtractionType = new Tonal();
        WallpaperManager wallpaperManager = mContext.getSystemService(WallpaperManager.class);

        if (wallpaperManager == null) {
            Log.w(TAG, "Can't listen to color changes!");
        } else {
            Parcelable wallpaperColorsObj;
            try {
                Method method = WallpaperManager.class
                        .getDeclaredMethod("getWallpaperColors", int.class);

                wallpaperColorsObj = (Parcelable) method.invoke(wallpaperManager,
                        WallpaperManager.FLAG_SYSTEM);
                extractInto(new WallpaperColorsCompat(wallpaperColorsObj), mSystemColors);
                wallpaperColorsObj = (Parcelable) method.invoke(wallpaperManager,
                        WallpaperManager.FLAG_LOCK);
                extractInto(new WallpaperColorsCompat(wallpaperColorsObj), mLockColors);
            } catch (Exception e) {
                Log.e(TAG, "reflection failed", e);
            }
        }
    }

    public GradientColors getColors(int which) {
        if (which == WallpaperManager.FLAG_LOCK) {
            return mLockColors;
        } else if (which == WallpaperManager.FLAG_SYSTEM) {
            return mSystemColors;
        } else {
            throw new IllegalArgumentException("which should be either FLAG_SYSTEM or FLAG_LOCK");
        }
    }

    private void extractInto(WallpaperColorsCompat inWallpaperColors, GradientColors outGradientColors) {
        applyFallback(outGradientColors);
        if (inWallpaperColors == null) {
            return;
        }
        mExtractionType.extractInto(inWallpaperColors, outGradientColors);
    }

    private void applyFallback(GradientColors outGradientColors) {
        outGradientColors.setMainColor(mMainFallbackColor);
        outGradientColors.setSecondaryColor(mSecondaryFallbackColor);
    }

    public static class GradientColors {
        private int mMainColor = FALLBACK_COLOR;
        private int mSecondaryColor = FALLBACK_COLOR;
        private boolean mSupportsDarkText;

        public void setMainColor(int mainColor) {
            mMainColor = mainColor;
        }

        public void setSecondaryColor(int secondaryColor) {
            mSecondaryColor = secondaryColor;
        }

        public void setSupportsDarkText(boolean supportsDarkText) {
            mSupportsDarkText = supportsDarkText;
        }

        public void set(GradientColors other) {
            mMainColor = other.mMainColor;
            mSecondaryColor = other.mSecondaryColor;
            mSupportsDarkText = other.mSupportsDarkText;
        }

        public int getMainColor() {
            return mMainColor;
        }

        public int getSecondaryColor() {
            return mSecondaryColor;
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
    }
}

