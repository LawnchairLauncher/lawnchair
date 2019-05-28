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

package com.android.launcher3;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class ResourceUtils {
    public static final String NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE = "navigation_bar_width";
    public static final String NAVBAR_BOTTOM_GESTURE_SIZE = "navigation_bar_gesture_height";


    public static int getNavbarSize(String resName, Resources res) {
        return getDimenByName(resName, res, 48);
    }

    private static int getDimenByName(String resName, Resources res, int defaultValue) {
        final int frameSize;
        final int frameSizeResID = res.getIdentifier(resName, "dimen", "android");
        if (frameSizeResID != 0) {
            frameSize = res.getDimensionPixelSize(frameSizeResID);
        } else {
            frameSize = pxFromDp(defaultValue, res.getDisplayMetrics());
        }
        return frameSize;
    }

    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, metrics));
    }
}
