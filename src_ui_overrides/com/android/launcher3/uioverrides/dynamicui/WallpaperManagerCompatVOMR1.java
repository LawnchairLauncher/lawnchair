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

import android.annotation.TargetApi;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.WallpaperManager.OnColorsChangedListener;
import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.reflect.Method;

@TargetApi(27)
public class WallpaperManagerCompatVOMR1 extends WallpaperManagerCompat {

    private static final String TAG = "WMCompatVOMR1";

    private final WallpaperManager mWm;
    private Method mWCColorHintsMethod;

    WallpaperManagerCompatVOMR1(Context context) throws Throwable {
        mWm = context.getSystemService(WallpaperManager.class);
        String className = WallpaperColors.class.getName();
        try {
            mWCColorHintsMethod = WallpaperColors.class.getDeclaredMethod("getColorHints");
        } catch (Exception exc) {
            Log.e(TAG, "getColorHints not available", exc);
        }
    }

    @Nullable
    @Override
    public WallpaperColorsCompat getWallpaperColors(int which) {
        return convertColorsObject(mWm.getWallpaperColors(which));
    }

    @Override
    public void addOnColorsChangedListener(final OnColorsChangedListenerCompat listener) {
        OnColorsChangedListener onChangeListener = new OnColorsChangedListener() {
            @Override
            public void onColorsChanged(WallpaperColors colors, int which) {
                listener.onColorsChanged(convertColorsObject(colors), which);
            }
        };
        mWm.addOnColorsChangedListener(onChangeListener, null);
    }

    private WallpaperColorsCompat convertColorsObject(WallpaperColors colors) {
        if (colors == null) {
            return null;
        }
        Color primary = colors.getPrimaryColor();
        Color secondary = colors.getSecondaryColor();
        Color tertiary = colors.getTertiaryColor();
        int primaryVal = primary != null ? primary.toArgb() : 0;
        int secondaryVal = secondary != null ? secondary.toArgb() : 0;
        int tertiaryVal = tertiary != null ? tertiary.toArgb() : 0;
        int colorHints = 0;
        try {
            if (mWCColorHintsMethod != null) {
                colorHints = (Integer) mWCColorHintsMethod.invoke(colors);
            }
        } catch (Exception exc) {
            Log.e(TAG, "error calling color hints", exc);
        }
        return new WallpaperColorsCompat(primaryVal, secondaryVal, tertiaryVal, colorHints);
    }
}
