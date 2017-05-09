package com.android.launcher3.dynamicui.colorextraction.types;

import com.android.launcher3.dynamicui.colorextraction.ColorExtractor;
import com.android.launcher3.dynamicui.colorextraction.WallpaperColorsCompat;


/**
 * Interface to allow various color extraction implementations.
 *
 * TODO remove this class if available by platform
 */
public interface ExtractionType {

    /**
     * Executes color extraction by reading WallpaperColors and setting
     * main and secondary colors on GradientColors.
     *
     * @param inWallpaperColors where to read from
     * @param outGradientColors object that should receive the colors
     */
    void extractInto(WallpaperColorsCompat inWallpaperColors,
                     ColorExtractor.GradientColors outGradientColors);
}
