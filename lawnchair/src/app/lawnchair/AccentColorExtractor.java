/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR condITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair;

import android.app.WallpaperColors;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.launcher3.widget.LocalColorExtractor;

import java.util.Map;

import app.lawnchair.theme.ThemeProvider;
import app.lawnchair.theme.color.AndroidColor;
import dev.kdrag0n.colorkt.Color;
import dev.kdrag0n.monet.theme.ColorScheme;

@RequiresApi(api = Build.VERSION_CODES.S)
public class AccentColorExtractor extends LocalColorExtractor implements ThemeProvider.ColorSchemeChangeListener {

    private final ThemeProvider mThemeProvider;
    private final RectF mTmpRect = new RectF();
    private Listener mListener;

    @Keep
    public AccentColorExtractor(Context context) {
        mThemeProvider = ThemeProvider.INSTANCE.get(context);
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        mListener = listener;
        notifyListener();
    }

    @Override
    public void setWorkspaceLocation(Rect pos, View child, int screenId) {

    }

    @Nullable
    protected SparseIntArray generateColorsOverride(ColorScheme colorScheme) {
        SparseIntArray colorRes = new SparseIntArray(5 * 13);

        addColorsToArray(colorScheme.getAccent1(), ACCENT1_RES, colorRes);
        addColorsToArray(colorScheme.getAccent2(), ACCENT2_RES, colorRes);
        addColorsToArray(colorScheme.getAccent3(), ACCENT3_RES, colorRes);
        addColorsToArray(colorScheme.getNeutral1(), NEUTRAL1_RES, colorRes);
        addColorsToArray(colorScheme.getNeutral2(), NEUTRAL2_RES, colorRes);

        return colorRes;
    }

    @Override
    public void applyColorsOverride(Context base, WallpaperColors colors) {
        RemoteViews.ColorResources res =
                RemoteViews.ColorResources.create(base, generateColorsOverride(colors));
        if (res != null) {
            res.apply(base);
        }
    }

    @Override
    public void onColorSchemeChanged() {
        notifyListener();
    }

    private void notifyListener() {
        if (mListener != null) {
            mListener.onColorsChanged(generateColorsOverride(mThemeProvider.getColorScheme()));
        }
    }

    // Shade number -> color resource ID maps
    private static final SparseIntArray ACCENT1_RES = new SparseIntArray(13);
    private static final SparseIntArray ACCENT2_RES = new SparseIntArray(13);
    private static final SparseIntArray ACCENT3_RES = new SparseIntArray(13);
    private static final SparseIntArray NEUTRAL1_RES = new SparseIntArray(13);
    private static final SparseIntArray NEUTRAL2_RES = new SparseIntArray(13);

    static {
        ACCENT1_RES.put(   0, android.R.color.system_accent1_0);
        ACCENT1_RES.put(  10, android.R.color.system_accent1_10);
        ACCENT1_RES.put(  50, android.R.color.system_accent1_50);
        ACCENT1_RES.put( 100, android.R.color.system_accent1_100);
        ACCENT1_RES.put( 200, android.R.color.system_accent1_200);
        ACCENT1_RES.put( 300, android.R.color.system_accent1_300);
        ACCENT1_RES.put( 400, android.R.color.system_accent1_400);
        ACCENT1_RES.put( 500, android.R.color.system_accent1_500);
        ACCENT1_RES.put( 600, android.R.color.system_accent1_600);
        ACCENT1_RES.put( 700, android.R.color.system_accent1_700);
        ACCENT1_RES.put( 800, android.R.color.system_accent1_800);
        ACCENT1_RES.put( 900, android.R.color.system_accent1_900);
        ACCENT1_RES.put(1000, android.R.color.system_accent1_1000);

        ACCENT2_RES.put(   0, android.R.color.system_accent2_0);
        ACCENT2_RES.put(  10, android.R.color.system_accent2_10);
        ACCENT2_RES.put(  50, android.R.color.system_accent2_50);
        ACCENT2_RES.put( 100, android.R.color.system_accent2_100);
        ACCENT2_RES.put( 200, android.R.color.system_accent2_200);
        ACCENT2_RES.put( 300, android.R.color.system_accent2_300);
        ACCENT2_RES.put( 400, android.R.color.system_accent2_400);
        ACCENT2_RES.put( 500, android.R.color.system_accent2_500);
        ACCENT2_RES.put( 600, android.R.color.system_accent2_600);
        ACCENT2_RES.put( 700, android.R.color.system_accent2_700);
        ACCENT2_RES.put( 800, android.R.color.system_accent2_800);
        ACCENT2_RES.put( 900, android.R.color.system_accent2_900);
        ACCENT2_RES.put(1000, android.R.color.system_accent2_1000);

        ACCENT3_RES.put(   0, android.R.color.system_accent3_0);
        ACCENT3_RES.put(  10, android.R.color.system_accent3_10);
        ACCENT3_RES.put(  50, android.R.color.system_accent3_50);
        ACCENT3_RES.put( 100, android.R.color.system_accent3_100);
        ACCENT3_RES.put( 200, android.R.color.system_accent3_200);
        ACCENT3_RES.put( 300, android.R.color.system_accent3_300);
        ACCENT3_RES.put( 400, android.R.color.system_accent3_400);
        ACCENT3_RES.put( 500, android.R.color.system_accent3_500);
        ACCENT3_RES.put( 600, android.R.color.system_accent3_600);
        ACCENT3_RES.put( 700, android.R.color.system_accent3_700);
        ACCENT3_RES.put( 800, android.R.color.system_accent3_800);
        ACCENT3_RES.put( 900, android.R.color.system_accent3_900);
        ACCENT3_RES.put(1000, android.R.color.system_accent3_1000);

        NEUTRAL1_RES.put(   0, android.R.color.system_neutral1_0);
        NEUTRAL1_RES.put(  10, android.R.color.system_neutral1_10);
        NEUTRAL1_RES.put(  50, android.R.color.system_neutral1_50);
        NEUTRAL1_RES.put( 100, android.R.color.system_neutral1_100);
        NEUTRAL1_RES.put( 200, android.R.color.system_neutral1_200);
        NEUTRAL1_RES.put( 300, android.R.color.system_neutral1_300);
        NEUTRAL1_RES.put( 400, android.R.color.system_neutral1_400);
        NEUTRAL1_RES.put( 500, android.R.color.system_neutral1_500);
        NEUTRAL1_RES.put( 600, android.R.color.system_neutral1_600);
        NEUTRAL1_RES.put( 700, android.R.color.system_neutral1_700);
        NEUTRAL1_RES.put( 800, android.R.color.system_neutral1_800);
        NEUTRAL1_RES.put( 900, android.R.color.system_neutral1_900);
        NEUTRAL1_RES.put(1000, android.R.color.system_neutral1_1000);

        NEUTRAL2_RES.put(   0, android.R.color.system_neutral2_0);
        NEUTRAL2_RES.put(  10, android.R.color.system_neutral2_10);
        NEUTRAL2_RES.put(  50, android.R.color.system_neutral2_50);
        NEUTRAL2_RES.put( 100, android.R.color.system_neutral2_100);
        NEUTRAL2_RES.put( 200, android.R.color.system_neutral2_200);
        NEUTRAL2_RES.put( 300, android.R.color.system_neutral2_300);
        NEUTRAL2_RES.put( 400, android.R.color.system_neutral2_400);
        NEUTRAL2_RES.put( 500, android.R.color.system_neutral2_500);
        NEUTRAL2_RES.put( 600, android.R.color.system_neutral2_600);
        NEUTRAL2_RES.put( 700, android.R.color.system_neutral2_700);
        NEUTRAL2_RES.put( 800, android.R.color.system_neutral2_800);
        NEUTRAL2_RES.put( 900, android.R.color.system_neutral2_900);
        NEUTRAL2_RES.put(1000, android.R.color.system_neutral2_1000);
    }

    private static void addColorsToArray(Map<Integer, Color> swatch,
                                         SparseIntArray resMap, SparseIntArray array) {
        for (Map.Entry<Integer, Color> entry : swatch.entrySet()) {
            int shade = entry.getKey();
            int resId = resMap.get(shade, -1);
            if (resId != -1) {
                AndroidColor color = (AndroidColor) entry.getValue();
                array.put(resId, color.getColor());
            }
        }
    }
}
