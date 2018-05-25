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
package com.android.launcher3.graphics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * Simple scrim which draws a color
 */
public class ColorScrim extends ViewScrim {

    private final int mColor;
    private final Interpolator mInterpolator;
    private int mCurrentColor;

    public ColorScrim(View view, int color, Interpolator interpolator) {
        super(view);
        mColor = color;
        mInterpolator = interpolator;
    }

    @Override
    protected void onProgressChanged() {
        mCurrentColor = ColorUtils.setAlphaComponent(mColor,
                Math.round(mInterpolator.getInterpolation(mProgress) * Color.alpha(mColor)));
    }

    @Override
    public void draw(Canvas canvas, int width, int height) {
        if (mProgress > 0) {
            canvas.drawColor(mCurrentColor);
        }
    }

    public static ColorScrim createExtractedColorScrim(View view) {
        WallpaperColorInfo colors = WallpaperColorInfo.getInstance(view.getContext());
        int alpha = view.getResources().getInteger(R.integer.extracted_color_gradient_alpha);
        ColorScrim scrim = new ColorScrim(view, ColorUtils.setAlphaComponent(
                colors.getSecondaryColor(), alpha), Interpolators.LINEAR);
        scrim.attach();
        return scrim;
    }
}
