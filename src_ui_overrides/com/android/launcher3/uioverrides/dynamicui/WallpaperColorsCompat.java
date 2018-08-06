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
package com.android.launcher3.uioverrides.dynamicui;

/**
 * A compatibility layer around platform implementation of WallpaperColors
 */
public class WallpaperColorsCompat {

    public static final int HINT_SUPPORTS_DARK_TEXT = 0x1;
    public static final int HINT_SUPPORTS_DARK_THEME = 0x2;

    private final int mPrimaryColor;
    private final int mSecondaryColor;
    private final int mTertiaryColor;
    private final int mColorHints;

    public WallpaperColorsCompat(int primaryColor, int secondaryColor, int tertiaryColor,
            int colorHints) {
        mPrimaryColor = primaryColor;
        mSecondaryColor = secondaryColor;
        mTertiaryColor = tertiaryColor;
        mColorHints = colorHints;
    }

    public int getPrimaryColor() {
        return mPrimaryColor;
    }

    public int getSecondaryColor() {
        return mSecondaryColor;
    }

    public int getTertiaryColor() {
        return mTertiaryColor;
    }

    public int getColorHints() {
        return mColorHints;
    }

}
