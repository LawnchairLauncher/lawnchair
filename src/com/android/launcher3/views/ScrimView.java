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
package com.android.launcher3.views;

import static android.support.v4.graphics.ColorUtils.compositeColors;
import static android.support.v4.graphics.ColorUtils.setAlphaComponent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener;
import com.android.launcher3.util.Themes;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView extends View implements Insettable, OnChangeListener {

    private final WallpaperColorInfo mWallpaperColorInfo;
    protected final int mEndScrim;

    protected float mMaxScrimAlpha;

    protected float mProgress = 1;
    protected int mScrimColor;

    protected int mCurrentFlatColor;
    protected int mEndFlatColor;
    protected int mEndFlatColorAlpha;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mWallpaperColorInfo = WallpaperColorInfo.getInstance(context);
        mEndScrim = Themes.getAttrColor(context, R.attr.allAppsScrimColor);

        mMaxScrimAlpha = 0.7f;
    }

    @Override
    public void setInsets(Rect insets) { }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperColorInfo.addOnChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperColorInfo.removeOnChangeListener(this);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        mScrimColor = wallpaperColorInfo.getMainColor();
        mEndFlatColor = compositeColors(mEndScrim, setAlphaComponent(
                mScrimColor, Math.round(mMaxScrimAlpha * 255)));
        mEndFlatColorAlpha = Color.alpha(mEndFlatColor);
        updateColors();
        invalidate();
    }

    public void setProgress(float progress) {
        if (mProgress != progress) {
            mProgress = progress;
            updateColors();
            invalidate();
        }
    }

    public void reInitUi() { }

    protected void updateColors() {
        mCurrentFlatColor = mProgress >= 1 ? 0 : setAlphaComponent(
                mEndFlatColor, Math.round((1 - mProgress) * mEndFlatColorAlpha));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentFlatColor != 0) {
            canvas.drawColor(mCurrentFlatColor);
        }
    }
}
