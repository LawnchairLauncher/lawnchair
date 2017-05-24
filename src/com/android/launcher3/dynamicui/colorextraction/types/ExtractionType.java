package com.android.launcher3.dynamicui.colorextraction.types;

import android.support.annotation.Nullable;
import android.util.Pair;

import com.android.launcher3.compat.WallpaperColorsCompat;


/**
 * Interface to allow various color extraction implementations.
 */
public interface ExtractionType {

    /**
     * Executes color extraction by reading WallpaperColors and setting
     * main and secondary colors.
     *
     * @param wallpaperColors where to read from
     * @return a pair of main and secondary color
     */
    @Nullable Pair<Integer, Integer> extractInto(WallpaperColorsCompat wallpaperColors);
}
