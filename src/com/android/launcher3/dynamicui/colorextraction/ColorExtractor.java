package com.android.launcher3.dynamicui.colorextraction;

import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.content.Context;
import android.graphics.Color;

import com.android.launcher3.compat.WallpaperColorsCompat;
import com.android.launcher3.compat.WallpaperManagerCompat;
import com.android.launcher3.dynamicui.colorextraction.types.ExtractionType;
import com.android.launcher3.dynamicui.colorextraction.types.Tonal;


/**
 * Class to process wallpaper colors and generate a tonal palette based on them.
 *
 * TODO remove this class if available by platform
 */
public class ColorExtractor {
    private static final int FALLBACK_COLOR = Color.WHITE;

    private final Context mContext;
    private int mMainFallbackColor = FALLBACK_COLOR;
    private int mSecondaryFallbackColor = FALLBACK_COLOR;
    private final GradientColors mSystemColors;
    private final ExtractionType mExtractionType;

    public ColorExtractor(Context context) {
        mContext = context;
        mSystemColors = new GradientColors();
        mExtractionType = new Tonal();

        extractFrom(WallpaperManagerCompat.getInstance(context).getWallpaperColors(FLAG_SYSTEM));
    }

    public GradientColors getColors() {
        return mSystemColors;
    }

    private void extractFrom(WallpaperColorsCompat inWallpaperColors) {
        applyFallback(mSystemColors);
        if (inWallpaperColors == null) {
            return;
        }
        mExtractionType.extractInto(inWallpaperColors, mSystemColors);
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

