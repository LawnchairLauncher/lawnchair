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

import android.content.Context;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.Log;

import com.android.launcher3.Utilities;

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
    // public static final int VIBRANT_INDEX = 2;
    // public static final int VIBRANT_DARK_INDEX = 3;
    // public static final int VIBRANT_LIGHT_INDEX = 4;
    // public static final int MUTED_INDEX = 5;
    // public static final int MUTED_DARK_INDEX = 6;
    // public static final int MUTED_LIGHT_INDEX = 7;

    public static final int NUM_COLOR_PROFILES = 2;
    private static final int VERSION = 1;

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

        if (!Utilities.ATLEAST_NOUGAT)
            encodedString = "1,771751935,0,"; //0x2DFFFFFF

        decodeFromString(encodedString);

        if (mColors[VERSION_INDEX] != VERSION) {
            ExtractionUtils.startColorExtractionService(context);
        }
    }

    /** @param index must be one of the index values defined at the top of this class. */
    public int getColor(int index, int defaultColor) {
        if (index > VERSION_INDEX && index < mColors.length) {
            return mColors[index];
        }
        return defaultColor;
    }

    /**
     * The hotseat's color is defined as follows:
     * - 12% black for super light wallpaper
     * - 18% white for super dark
     * - 25% white otherwise
     */
    public void updateHotseatPalette(Palette hotseatPalette) {
        int hotseatColor;
        if (hotseatPalette != null && ExtractionUtils.isSuperLight(hotseatPalette)) {
            hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (0.12f * 255));
        } else if (hotseatPalette != null && ExtractionUtils.isSuperDark(hotseatPalette)) {
            hotseatColor = ColorUtils.setAlphaComponent(Color.WHITE, (int) (0.18f * 255));
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
