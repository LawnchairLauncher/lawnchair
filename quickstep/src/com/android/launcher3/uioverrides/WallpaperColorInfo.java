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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.android.systemui.shared.system.TonalCompat;
import com.android.systemui.shared.system.TonalCompat.ExtractionInfo;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.P)
public class WallpaperColorInfo implements OnColorsChangedListener {

    private static final int MAIN_COLOR_LIGHT = 0xffdadce0;
    private static final int MAIN_COLOR_DARK = 0xff202124;
    private static final int MAIN_COLOR_REGULAR = 0xff000000;

    private static final Object sInstanceLock = new Object();
    private static WallpaperColorInfo sInstance;

    public static WallpaperColorInfo getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new WallpaperColorInfo(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManager mWallpaperManager;
    private final TonalCompat mTonalCompat;

    private ExtractionInfo mExtractionInfo;

    private OnChangeListener[] mTempListeners = new OnChangeListener[0];

    private WallpaperColorInfo(Context context) {
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        mTonalCompat = new TonalCompat(context);

        mWallpaperManager.addOnColorsChangedListener(this, new Handler(Looper.getMainLooper()));
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    public int getMainColor() {
        return mExtractionInfo.mainColor;
    }

    public int getSecondaryColor() {
        return mExtractionInfo.secondaryColor;
    }

    public boolean isDark() {
        return mExtractionInfo.supportsDarkTheme;
    }

    public boolean supportsDarkText() {
        return mExtractionInfo.supportsDarkText;
    }

    public boolean isMainColorDark() {
        return mExtractionInfo.mainColor == MAIN_COLOR_DARK;
    }

    @Override
    public void onColorsChanged(WallpaperColors colors, int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            update(colors);
            notifyChange();
        }
    }

    private void update(WallpaperColors wallpaperColors) {
        mExtractionInfo = mTonalCompat.extractDarkColors(wallpaperColors);
    }

    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

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

    public interface OnChangeListener {
        void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo);
    }
}
