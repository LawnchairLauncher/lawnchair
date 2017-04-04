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
    public static final int HOTSEAT_INDEX = 1;
    public static final int STATUS_BAR_INDEX = 2;
    public static final int DOMINANT_INDEX = 3;
    public static final int VIBRANT_INDEX = 4;
    public static final int VIBRANT_DARK_INDEX = 5;
    public static final int VIBRANT_LIGHT_INDEX = 6;
    public static final int MUTED_INDEX = 7;
    public static final int MUTED_DARK_INDEX = 8;
    public static final int MUTED_LIGHT_INDEX = 9;
    public static final int VIBRANT_FOREGROUND_INDEX = 10;

    public static final int NUM_COLOR_PROFILES = 10;
    private static final int VERSION = 5;

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
        String encodedString = Utilities.getPrefs(context).getString(
                ExtractionUtils.EXTRACTED_COLORS_PREFERENCE_KEY, VERSION + "");

        decodeFromString(encodedString);

        if (mColors[VERSION_INDEX] != VERSION) {
            ExtractionUtils.startColorExtractionService(context);
        }
    }

    /**
     * @param index must be one of the index values defined at the top of this class.
     */
    public int getColor(int index, int defaultColor) {
        if (index > VERSION_INDEX && index < mColors.length) {
            return mColors[index];
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
            int dominant = palette.getDominantColor(DEFAULT_COLOR);
            int muted_dark = palette.getDarkMutedColor(DEFAULT_DARK);
            int muted_light = palette.getLightMutedColor(DEFAULT_LIGHT);
            int muted = palette.getMutedColor(DEFAULT_COLOR);
            int vibrant_dark = palette.getDarkVibrantColor(DEFAULT_DARK);
            int vibrant_light = palette.getLightVibrantColor(DEFAULT_LIGHT);
            Palette.Swatch vibrant = palette.getVibrantSwatch();
            setColorAtIndex(DOMINANT_INDEX, dominant);
            setColorAtIndex(VIBRANT_DARK_INDEX, vibrant_dark);
            setColorAtIndex(VIBRANT_LIGHT_INDEX, vibrant_light);
            setColorAtIndex(VIBRANT_INDEX, vibrant != null ? vibrant.getRgb() : DEFAULT_COLOR);
            setColorAtIndex(VIBRANT_FOREGROUND_INDEX, vibrant != null ? vibrant.getBodyTextColor() : Color.BLACK);
            setColorAtIndex(MUTED_INDEX, muted);
            setColorAtIndex(MUTED_DARK_INDEX, muted_dark);
            setColorAtIndex(MUTED_LIGHT_INDEX, muted_light);
        }
    }

    /**
     * The hotseat's color is defined as follows:
     * - 20% darkMuted or 12% black for super light wallpaper
     * - 25% lightMuted or 18% white for super dark
     * - 40% lightVibrant or 25% white otherwise
     */
    public void updateHotseatPalette(Context context, Palette hotseatPalette) {
        int hotseatColor;
        if (hotseatPalette != null){
            boolean shouldUseExtractedColors = FeatureFlags.hotseatShouldUseExtractedColors(context);
            if(ExtractionUtils.isSuperLight(hotseatPalette)) {
                if (shouldUseExtractedColors){
                    int baseColor = hotseatPalette.getDarkMutedColor(hotseatPalette.getDarkVibrantColor(Color.BLACK));
                    hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.20f * 255));
                } else {
                    hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (0.12f * 255));
                }
            } else if (ExtractionUtils.isSuperDark(hotseatPalette)) {
                if(shouldUseExtractedColors){
                    int baseColor = hotseatPalette.getLightMutedColor(hotseatPalette.getLightVibrantColor(Color.WHITE));
                    hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.25f * 255));
                } else {
                    hotseatColor = ColorUtils.setAlphaComponent(Color.WHITE, (int) (0.18f * 255));
                }
            } else {
                if(shouldUseExtractedColors){
                    int baseColor = hotseatPalette.getLightVibrantColor(hotseatPalette.getLightMutedColor(Color.WHITE));
                    hotseatColor = ColorUtils.setAlphaComponent(baseColor, (int) (0.40f * 255));
                } else {
                    hotseatColor = ColorUtils.setAlphaComponent(Color.WHITE, (int) (0.25f * 255));
                }
            }
        } else {
            hotseatColor = ColorUtils.setAlphaComponent(Color.WHITE, (int) (0.25f * 255));
        }
        setColorAtIndex(HOTSEAT_INDEX, hotseatColor);
    }

    public void updateStatusBarPalette(Palette statusBarPalette) {
        setColorAtIndex(STATUS_BAR_INDEX, ExtractionUtils.isSuperLight(statusBarPalette) ?
                DEFAULT_LIGHT : DEFAULT_DARK);
    }
}
