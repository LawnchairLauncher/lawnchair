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

package com.android.launcher3.graphics;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.graphics.Canvas;
import android.util.Property;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * Contains general scrim properties such as wallpaper-extracted color that subclasses can use.
 */
public class Scrim implements View.OnAttachStateChangeListener,
        WallpaperColorInfo.OnChangeListener {

    public static Property<Scrim, Float> SCRIM_PROGRESS =
            new Property<Scrim, Float>(Float.TYPE, "scrimProgress") {
                @Override
                public Float get(Scrim scrim) {
                    return scrim.mScrimProgress;
                }

                @Override
                public void set(Scrim scrim, Float value) {
                    scrim.setScrimProgress(value);
                }
            };

    protected final Launcher mLauncher;
    protected final WallpaperColorInfo mWallpaperColorInfo;
    protected final View mRoot;

    protected float mScrimProgress;
    protected int mScrimColor;
    protected int mScrimAlpha = 0;

    public Scrim(View view) {
        mRoot = view;
        mLauncher = Launcher.getLauncher(view.getContext());
        mWallpaperColorInfo = WallpaperColorInfo.getInstance(mLauncher);

        view.addOnAttachStateChangeListener(this);
    }

    public void draw(Canvas canvas) {
        canvas.drawColor(setColorAlphaBound(mScrimColor, mScrimAlpha));
    }

    private void setScrimProgress(float progress) {
        if (mScrimProgress != progress) {
            mScrimProgress = progress;
            mScrimAlpha = Math.round(255 * mScrimProgress);
            invalidate();
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mWallpaperColorInfo.addOnChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mWallpaperColorInfo.removeOnChangeListener(this);
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        mScrimColor = wallpaperColorInfo.getMainColor();
        if (mScrimAlpha > 0) {
            invalidate();
        }
    }

    public void invalidate() {
        mRoot.invalidate();
    }
}
