/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.app.WallpaperColors;
import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;

/** Extracts the colors we need from the wallpaper at given locations. */
public class LocalColorExtractor implements ResourceBasedOverride {

    /**
     * Creates a new instance of LocalColorExtractor
     */
    public static LocalColorExtractor newInstance(Context context) {
        return Overrides.getObject(LocalColorExtractor.class, context.getApplicationContext(),
                R.string.local_colors_extraction_class);
    }

    /**
     * Updates the base context to contain the colors override
     */
    public void applyColorsOverride(Context base, WallpaperColors colors) { }

    /**
     * Generates color resource overrides from {@link WallpaperColors}.
     */
    @Nullable
    public SparseIntArray generateColorsOverride(WallpaperColors colors) {
        return null;
    }
}
