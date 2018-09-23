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

import android.content.Context;
import android.graphics.Color;
import android.util.Pair;

import java.util.ArrayList;

import static android.app.WallpaperManager.FLAG_SYSTEM;

public class WallpaperColorInfoVL extends WallpaperColorInfo implements WallpaperManagerCompat.OnColorsChangedListenerCompat {

    private static final int FALLBACK_COLOR = Color.WHITE;

    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManagerCompat mWallpaperManager;
    private final ColorExtractionAlgorithm mExtractionType;
    private int mMainColor;
    private int mSecondaryColor;
    private int mActualMainColor;
    private int mActualSecondaryColor;
    private int mTertiaryColor;
    private boolean mIsDark;
    private boolean mSupportsDarkText;

    private OnChangeListener[] mTempListeners;

    WallpaperColorInfoVL(Context context) {
        mWallpaperManager = WallpaperManagerCompat.getInstance(context);
        mWallpaperManager.addOnColorsChangedListener(this);
        mExtractionType = ColorExtractionAlgorithm.newInstance(context);
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    @Override
    public int getMainColor() {
        return mMainColor;
    }


    @Override
    public int getActualMainColor() {
        return mActualMainColor;
    }

    @Override
    public int getSecondaryColor() {
        return mSecondaryColor;
    }

    @Override
    public int getActualSecondaryColor() {
        return mActualSecondaryColor;
    }

    @Override
    public int getTertiaryColor() {
        return mTertiaryColor;
    }

    @Override
    public boolean isDark() {
        return mIsDark;
    }

    @Override
    public boolean supportsDarkText() {
        return mSupportsDarkText;
    }

    @Override
    public void onColorsChanged(WallpaperColorsCompat colors, int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            update(colors);
            notifyChange();
        }
    }

    private void update(WallpaperColorsCompat wallpaperColors) {
        if (wallpaperColors != null) {
            mActualMainColor = wallpaperColors.getPrimaryColor();
            mActualSecondaryColor = wallpaperColors.getSecondaryColor();
            mTertiaryColor = wallpaperColors.getTertiaryColor();
            mSupportsDarkText = (wallpaperColors.getColorHints()
                    & WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) > 0;
            mIsDark = (wallpaperColors.getColorHints()
                    & WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME) > 0;
        } else {
            mActualMainColor = mActualSecondaryColor = mTertiaryColor = FALLBACK_COLOR;
            mSupportsDarkText = mIsDark = false;
        }
        Pair<Integer, Integer> colors = mExtractionType.extractInto(wallpaperColors);
        if (colors != null) {
            mMainColor = colors.first;
            mSecondaryColor = colors.second;
        } else {
            mMainColor = FALLBACK_COLOR;
            mSecondaryColor = FALLBACK_COLOR;
        }
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
        OnChangeListener[] copy =
                mTempListeners != null && mTempListeners.length == mListeners.size() ?
                        mTempListeners : new OnChangeListener[mListeners.size()];

        // Create a new array to avoid concurrent modification when the activity destroys itself.
        mTempListeners = mListeners.toArray(copy);
        for (OnChangeListener listener : mTempListeners) {
            listener.onExtractedColorsChanged(this);
        }
    }
}