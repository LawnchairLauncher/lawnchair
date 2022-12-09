/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.testing.shared;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class ResourceUtils {
    private static final float EPSILON = 0.0001f;
    public static final int DEFAULT_NAVBAR_VALUE = 48;
    public static final int INVALID_RESOURCE_HANDLE = -1;
    public static final String NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE = "navigation_bar_width";
    public static final String NAVBAR_BOTTOM_GESTURE_SIZE = "navigation_bar_gesture_height";
    public static final String NAVBAR_BOTTOM_GESTURE_LARGER_SIZE =
            "navigation_bar_gesture_larger_height";

    public static final String NAVBAR_HEIGHT = "navigation_bar_height";
    public static final String NAVBAR_HEIGHT_LANDSCAPE = "navigation_bar_height_landscape";

    public static final String STATUS_BAR_HEIGHT = "status_bar_height";
    public static final String STATUS_BAR_HEIGHT_LANDSCAPE = "status_bar_height_landscape";
    public static final String STATUS_BAR_HEIGHT_PORTRAIT = "status_bar_height_portrait";

    public static final String NAV_BAR_INTERACTION_MODE_RES_NAME = "config_navBarInteractionMode";

    public static int getNavbarSize(String resName, Resources res) {
        return getDimenByName(resName, res, DEFAULT_NAVBAR_VALUE);
    }

    public static int getDimenByName(String resName, Resources res, int defaultValue) {
        final int frameSize;
        final int frameSizeResID = res.getIdentifier(resName, "dimen", "android");
        if (frameSizeResID != 0) {
            frameSize = res.getDimensionPixelSize(frameSizeResID);
        } else {
            frameSize = pxFromDp(defaultValue, res.getDisplayMetrics());
        }
        return frameSize;
    }

    public static boolean getBoolByName(String resName, Resources res, boolean defaultValue) {
        final boolean val;
        final int resId = res.getIdentifier(resName, "bool", "android");
        if (resId != 0) {
            val = res.getBoolean(resId);
        } else {
            val = defaultValue;
        }
        return val;
    }

    public static int getIntegerByName(String resName, Resources res, int defaultValue) {
        int resId = res.getIdentifier(resName, "integer", "android");
        return resId != 0 ? res.getInteger(resId) : defaultValue;
    }

    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return pxFromDp(size, metrics, 1f);
    }

    public static int pxFromDp(float size, DisplayMetrics metrics, float scale) {
        float value = scale * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, metrics);
        return size < 0 ? INVALID_RESOURCE_HANDLE : roundPxValueFromFloat(value);
    }

    /**
     * Rounds a pixel value, taking into account floating point errors.
     *
     * <p>If a dp (or sp) value typically returns a half pixel, such as 20dp at a 2.625 density
     * returning 52.5px, there is a small chance that due to floating-point errors, the value will
     * be stored as 52.499999. As we round to the nearest pixel, this could cause a 1px difference
     * in final values, which we correct for in this method.
     */
    public static int roundPxValueFromFloat(float value) {
        float fraction = (float) (value - Math.floor(value));
        if (Math.abs(0.5f - fraction) < EPSILON) {
            // Note: we add for negative values as well, as Math.round brings -.5 to the next
            // "highest" value, e.g. Math.round(-2.5) == -2 [i.e. (int)Math.floor(a + 0.5d)]
            value += EPSILON;
        }
        return Math.round(value);
    }
}
