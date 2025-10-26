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

package com.android.launcher3.graphics;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;

import com.android.launcher3.util.Themes;

import java.lang.IllegalArgumentException;

/**
 * Contains colors based on the dominant color of an icon.
 */
public class IconPalette {
    private static final String TAG = "IconPalette";

    private static final float MIN_PRELOAD_COLOR_SATURATION = 0.2f;
    private static final float MIN_PRELOAD_COLOR_LIGHTNESS = 0.6f;

    /**
     * Returns a color suitable for the progress bar color of preload icon.
     */
    public static int getPreloadProgressColor(Context context, int dominantColor) {
        int result = dominantColor;

        // Make sure that the dominant color has enough saturation to be visible properly.
        float[] hsv = new float[3];
        Color.colorToHSV(result, hsv);
        if (hsv[1] < MIN_PRELOAD_COLOR_SATURATION) {
            result = Themes.getColorAccent(context);
        } else {
            hsv[2] = Math.max(MIN_PRELOAD_COLOR_LIGHTNESS, hsv[2]);
            result = Color.HSVToColor(hsv);
        }
        return result;
    }
}
