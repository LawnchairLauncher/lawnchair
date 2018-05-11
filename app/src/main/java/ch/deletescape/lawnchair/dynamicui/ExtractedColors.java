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

import android.content.Context;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.Log;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.config.FeatureFlags;

/**
 * Saves and loads colors extracted from the wallpaper, as well as the associated wallpaper id.
 */
public class ExtractedColors {
    private static final String TAG = "ExtractedColors";

    public static final int DEFAULT_LIGHT = Color.WHITE;
    public static final int DEFAULT_DARK = Color.BLACK;
    public static final int DEFAULT_COLOR = DEFAULT_LIGHT;

    // These color profile indices should NOT be changed, since they are used when saving and
    // loading extracted colors. New colors should always be added at the end.
    public static final int VERSION_INDEX = 0;
    private static final int HOTSEAT_LIGHT_MUTED_INDEX = 1;
    public static final int STATUS_BAR_INDEX = 2;
    public static final int DOMINANT_INDEX = 3;
    public static final int VIBRANT_INDEX = 4;
    public static final int VIBRANT_DARK_INDEX = 5;
    public static final int VIBRANT_LIGHT_INDEX = 6;
    public static final int MUTED_INDEX = 7;
    public static final int MUTED_DARK_INDEX = 8;
    public static final int MUTED_LIGHT_INDEX = 9;
    public static final int VIBRANT_FOREGROUND_INDEX = 10;
    public static final int DOMINANT_FOREGROUND_INDEX = 11;
    private static final int HOTSEAT_DARK_MUTED_INDEX = 12;
    private static final int HOTSEAT_LIGHT_VIBRANT_INDEX = 13;
    private static final int HOTSEAT_DARK_VIBRANT_INDEX = 14;
    private static final int IS_SUPER_LIGHT = 15;
    private static final int IS_SUPER_DARK = 16;
    public static final int NAVIGATION_BAR_INDEX = 17;

    public static final int NUM_COLOR_PROFILES = 17;
    public static final int VERSION = 8;

    private static final String COLOR_SEPARATOR = ",";

    private int[] mColors;

    public ExtractedColors() {
        // The first entry is reserved for the version number.
        mColors = new int[NUM_COLOR_PROFILES + 1];
        mColors[VERSION_INDEX] = VERSION;
    }

    public void setColorAtIndex(int index, int color) {
        if (index > VERSION_INDEX && index < mColors.length) {
            mColors[index] = color;
        } else {
            Log.e(TAG, "Attempted to set a color at an invalid index " + index);
        }
    }

    /**
     * Encodes {@link #mColors} as a comma-separated String.
     */
    String encodeAsString() {
        StringBuilder colorsStringBuilder = new StringBuilder();
        for (int color : mColors) {
            colorsStringBuilder.append(color).append(COLOR_SEPARATOR);
        }
        return colorsStringBuilder.toString();
    }

    /**
     * Decodes a comma-separated String into {@link #mColors}.
     */
    void decodeFromString(String colorsString) {
        String[] splitColorsString = colorsString.split(COLOR_SEPARATOR);
        mColors = new int[splitColorsString.length];
        for (int i = 0; i < mColors.length; i++) {
            mColors[i] = Integer.parseInt(splitColorsString[i]);
        }
    }

    /**
     * Loads colors and wallpaper id from {@link Utilities#getPrefs(Context)}.
     * These were saved there in {@link ColorExtractionService}.
     */
    public void load(Context context) {
        String encodedString = Utilities.getPrefs(context).getExtractedColorsPreference();

        decodeFromString(encodedString);

        //if (mColors[VERSION_INDEX] != VERSION) {
        //   ExtractionUtils.startColorExtractionService(context);
        //}
    }

    /**
     * @param index must be one of the index values defined at the top of this class.
     */
    public int getColor(int index, int defaultColor) {
        if (index > VERSION_INDEX && index < mColors.length) {
            int color = mColors[index];
            return color == -1 ? defaultColor : color;
        }
        return defaultColor;
    }

    /**
     * Updates colors based on the palette.
     * If the palette is null, the default color is used in all cases.
     */
    public void updatePalette(Palette palette) {
        if (palette == null) {
            for (int i = 0; i < NUM_COLOR_PROFILES; i++) {
                setColorAtIndex(i, ExtractedColors.DEFAULT_COLOR);
            }
        } else {
            Palette.Swatch dominant = palette.getDominantSwatch();
            int muted_dark = palette.getDarkMutedColor(-1);
            int muted_light = palette.getLightMutedColor(-1);
            int muted = palette.getMutedColor(-1);
            int vibrant_dark = palette.getDarkVibrantColor(-1);
            int vibrant_light = palette.getLightVibrantColor(-1);
            Palette.Swatch vibrant = palette.getVibrantSwatch();
            setColorAtIndex(DOMINANT_INDEX, dominant != null ? dominant.getRgb() : -1);
            setColorAtIndex(DOMINANT_FOREGROUND_INDEX, dominant != null ? ColorUtils.setAlphaComponent(dominant.getBodyTextColor(), 255) : -1);
            setColorAtIndex(VIBRANT_DARK_INDEX, vibrant_dark);
            setColorAtIndex(VIBRANT_LIGHT_INDEX, vibrant_light);
            setColorAtIndex(VIBRANT_INDEX, vibrant != null ? vibrant.getRgb() : -1);
            setColorAtIndex(VIBRANT_FOREGROUND_INDEX, vibrant != null ? ColorUtils.setAlphaComponent(vibrant.getBodyTextColor(), 255) : -1);
            setColorAtIndex(MUTED_INDEX, muted);
            setColorAtIndex(MUTED_DARK_INDEX, muted_dark);
            setColorAtIndex(MUTED_LIGHT_INDEX, muted_light);
        }
    }

    public void updateHotseatPalette(Palette hotseatPalette) {
        if (hotseatPalette != null) {
            if (ExtractionUtils.isSuperLight(hotseatPalette)) {
                setColorAtIndex(IS_SUPER_LIGHT, 1);
                setColorAtIndex(IS_SUPER_DARK, 0);
            } else if (ExtractionUtils.isSuperDark(hotseatPalette)) {
                setColorAtIndex(IS_SUPER_LIGHT, 0);
                setColorAtIndex(IS_SUPER_DARK, 1);
            } else {
                setColorAtIndex(IS_SUPER_LIGHT, 0);
                setColorAtIndex(IS_SUPER_DARK, 0);
            }
            setColorAtIndex(HOTSEAT_DARK_MUTED_INDEX, hotseatPalette.getDarkMutedColor(-1));
            setColorAtIndex(HOTSEAT_DARK_VIBRANT_INDEX, hotseatPalette.getDarkVibrantColor(-1));
            setColorAtIndex(HOTSEAT_LIGHT_MUTED_INDEX, hotseatPalette.getLightMutedColor(-1));
            setColorAtIndex(HOTSEAT_LIGHT_VIBRANT_INDEX, hotseatPalette.getLightVibrantColor(-1));
        }
    }

    /**
     * The hotseat's color is defined as follows:
     * - 20% darkMuted or 12% black for super light wallpaper
     * - 25% lightMuted or 18% white for super dark
     * - 40% lightVibrant or 25% white otherwise
     */
    public int getHotseatColor(Context context) {
        if (Utilities.getPrefs(context).getTransparentHotseat()) {
            return Color.TRANSPARENT;
        }
        int hotseatColor;
        boolean shouldUseExtractedColors = Utilities.getPrefs(context).getHotseatShouldUseExtractedColors();
        if (getColor(IS_SUPER_LIGHT, 0) == 1) {
            if (shouldUseExtractedColors) {
                int baseColor = getColor(HOTSEAT_DARK_MUTED_INDEX, getColor(HOTSEAT_DARK_VIBRANT_INDEX, Color.BLACK));
                hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.20f * 255));
            } else {
                hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (0.12f * 255));
            }
        } else if (getColor(IS_SUPER_DARK, 0) == 1) {
            if (shouldUseExtractedColors) {
                int baseColor = getColor(HOTSEAT_LIGHT_MUTED_INDEX, getColor(HOTSEAT_LIGHT_VIBRANT_INDEX, Color.WHITE));
                hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.25f * 255));
            } else {
                hotseatColor = ColorUtils.setAlphaComponent(FeatureFlags.INSTANCE.useDarkTheme(FeatureFlags.DARK_ALLAPPS) ? Color.BLACK : Color.WHITE, (int) (0.18f * 255));
            }
        } else {
            if (shouldUseExtractedColors) {
                int baseColor = getColor(HOTSEAT_LIGHT_VIBRANT_INDEX, getColor(HOTSEAT_LIGHT_MUTED_INDEX, Color.WHITE));
                hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.40f * 255));
            } else {
                hotseatColor = ColorUtils.setAlphaComponent(FeatureFlags.INSTANCE.useDarkTheme(FeatureFlags.DARK_ALLAPPS) ? Color.BLACK : Color.WHITE, (int) (0.25f * 255));
            }
        }
        boolean useCustomOpacity = Utilities.getPrefs(context).getHotseatShouldUseCustomOpacity();
        if (useCustomOpacity) {
            float customOpacity = Utilities.getPrefs(context).getHotseatCustomOpacity();
            hotseatColor = ColorUtils.setAlphaComponent(hotseatColor, (int) (customOpacity * 255));
        }
        return hotseatColor;
    }

    public void updateStatusBarPalette(Palette statusBarPalette) {
        setColorAtIndex(STATUS_BAR_INDEX, ExtractionUtils.isSuperLight(statusBarPalette) ?
                DEFAULT_LIGHT : DEFAULT_DARK);
    }

    public void updateNavigationBarPalette(Palette navigationBarPalette) {
        setColorAtIndex(NAVIGATION_BAR_INDEX, ExtractionUtils.isSuperLight(navigationBarPalette) ?
                DEFAULT_LIGHT : DEFAULT_DARK);
    }

    public boolean isLightStatusBar() {
        return getColor(STATUS_BAR_INDEX, DEFAULT_LIGHT) == DEFAULT_LIGHT;
    }

    public boolean isLightNavigationBar() {
        return getColor(NAVIGATION_BAR_INDEX, DEFAULT_LIGHT) == DEFAULT_LIGHT;
    }
}
