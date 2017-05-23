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
package com.android.launcher3.compat;

import android.util.SparseIntArray;

/**
 * A compatibility layer around platform implementation of WallpaperColors
 */
public class WallpaperColorsCompat {

    private final SparseIntArray mColors;
    private final boolean mSupportsDarkText;

    public WallpaperColorsCompat(SparseIntArray colors, boolean supportsDarkText) {
        mColors = colors;
        mSupportsDarkText = supportsDarkText;
    }

    /**
     * A map of color code to their occurrences. The bigger the int, the more relevant the color.
     */
    public SparseIntArray getColors() {
        return mColors;
    }

    public boolean supportsDarkText() {
        return mSupportsDarkText;
    }
}
