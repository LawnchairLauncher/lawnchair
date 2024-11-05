/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.app.WallpaperColors;
import android.content.Context;

import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.colorextraction.types.Tonal;

public class TonalCompat {

    private final Tonal mTonal;

    public TonalCompat(Context context) {
        mTonal = new Tonal(context);
    }

    public ExtractionInfo extractDarkColors(WallpaperColors colors) {
        GradientColors darkColors = new GradientColors();
        mTonal.extractInto(colors, new GradientColors(), darkColors, new GradientColors());

        ExtractionInfo result = new ExtractionInfo();
        result.mainColor = darkColors.getMainColor();
        result.secondaryColor = darkColors.getSecondaryColor();
        result.supportsDarkText = darkColors.supportsDarkText();
        if (colors != null) {
            result.supportsDarkTheme =
                    (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0;
        }
        return result;
    }

    public static class ExtractionInfo {
        public int mainColor;
        public int secondaryColor;
        public boolean supportsDarkText;
        public boolean supportsDarkTheme;
    }
}
