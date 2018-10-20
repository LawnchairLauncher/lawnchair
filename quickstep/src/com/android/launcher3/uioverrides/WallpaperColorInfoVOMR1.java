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

package com.android.launcher3.uioverrides;

import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.annotation.TargetApi;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.WallpaperManager.OnColorsChangedListener;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.android.systemui.shared.system.TonalCompat;
import com.android.systemui.shared.system.TonalCompat.ExtractionInfo;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.O_MR1)
public class WallpaperColorInfoVOMR1 extends WallpaperColorInfo implements OnColorsChangedListener {

    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManager mWallpaperManager;
    private final TonalCompat mTonalCompat;

    private WallpaperColors mColors;
    private ExtractionInfo mExtractionInfo;

    private OnChangeListener[] mTempListeners = new OnChangeListener[0];

    WallpaperColorInfoVOMR1(Context context) {
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        mTonalCompat = new TonalCompat(context);

        mWallpaperManager.addOnColorsChangedListener(this, new Handler(Looper.getMainLooper()));
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    @Override
    public int getMainColor() {
        return mExtractionInfo.mainColor;
    }

    @Override
    public int getActualMainColor() {
        return mColors == null ? Color.TRANSPARENT : mColors.getPrimaryColor().toArgb();
    }

    @Override
    public int getSecondaryColor() {
        return mExtractionInfo.secondaryColor;
    }

    @Override
    public int getActualSecondaryColor() {
        Color secondary = mColors == null ? null : mColors.getSecondaryColor();
        return secondary == null ? Color.TRANSPARENT : secondary.toArgb();
    }

    @Override
    public int getTertiaryColor() {
        Color tertiary = mColors == null ? null : mColors.getTertiaryColor();
        return tertiary == null ? Color.TRANSPARENT : tertiary.toArgb();
    }

    @Override
    public boolean isDark() {
        return mExtractionInfo.supportsDarkTheme;
    }

    @Override
    public boolean supportsDarkText() {
        return mExtractionInfo.supportsDarkText;
    }

    @Override
    public void onColorsChanged(WallpaperColors colors, int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            update(colors);
            notifyChange();
        }
    }

    private void update(WallpaperColors wallpaperColors) {
        mColors = wallpaperColors;
        mExtractionInfo = mTonalCompat.extractDarkColors(wallpaperColors);
    }

    @Override
    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeOnChangeListener(OnChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyChange() {
        // Create a new array to avoid concurrent modification when the activity destroys itself.
        mTempListeners = mListeners.toArray(mTempListeners);
        for (OnChangeListener listener : mTempListeners) {
            if (listener != null) {
                listener.onExtractedColorsChanged(this);
            }
        }
    }
}
