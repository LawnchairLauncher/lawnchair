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

import static androidx.core.graphics.ColorUtils.compositeColors;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener;
import com.android.launcher3.util.Themes;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView<T extends Launcher> extends View implements Insettable, OnChangeListener {
    private static final float STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD = .1f;

    protected final T mLauncher;
    private final WallpaperColorInfo mWallpaperColorInfo;
    protected final int mEndScrim;
    protected final boolean mIsScrimDark;

    protected float mMaxScrimAlpha;

    protected float mProgress = 1;
    protected int mScrimColor;

    protected int mCurrentFlatColor;
    protected int mEndFlatColor;
    protected int mEndFlatColorAlpha;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = Launcher.cast(Launcher.getLauncher(context));
        mWallpaperColorInfo = WallpaperColorInfo.INSTANCE.get(context);
        mEndScrim = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mIsScrimDark = ColorUtils.calculateLuminance(mEndScrim) < 0.5f;

        mMaxScrimAlpha = 0.7f;
        setFocusable(false);
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
        mEndFlatColor = compositeColors(mEndScrim, setColorAlphaBound(
                mScrimColor, Math.round(mMaxScrimAlpha * 255)));
        mEndFlatColorAlpha = Color.alpha(mEndFlatColor);
        updateColors();
        invalidate();
    }

    public void setProgress(float progress) {
        if (mProgress != progress) {
            mProgress = progress;
            updateColors();
            updateSysUiColors();
            invalidate();
        }
    }

    public void reInitUi() { }

    protected void updateColors() {
        mCurrentFlatColor = mProgress >= 1 ? 0 : setColorAlphaBound(
                mEndFlatColor, Math.round((1 - mProgress) * mEndFlatColorAlpha));
    }

    protected void updateSysUiColors() {
        // Use a light system UI (dark icons) if all apps is behind at least half of the
        // status bar.
        boolean forceChange = mProgress <= STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD;
        if (forceChange) {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, !mIsScrimDark);
        } else {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, 0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentFlatColor != 0) {
            canvas.drawColor(mCurrentFlatColor);
        }
    }
}
