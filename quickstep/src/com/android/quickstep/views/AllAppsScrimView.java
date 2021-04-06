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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;

/**
 * Scrim used for all-apps background. uses interpolator to coordinate fade in with
 * all-apps contents
 *
 * Note: ranges are inverted because progress goes from 1 to 0 for NORMAL->AllAPPS
 */
public class AllAppsScrimView extends ScrimView<BaseQuickstepLauncher> {

    private static final float TINT_DECAY_MULTIPLIER = .5f;

    //min progress for scrim to become visible
    private static final float SCRIM_VISIBLE_THRESHOLD = .9f;
    //max progress where scrim alpha animates.
    private static final float SCRIM_SOLID_THRESHOLD = .5f;
    private final Interpolator mScrimInterpolator = Interpolators.clampToProgress(ACCEL,
            SCRIM_SOLID_THRESHOLD,
            SCRIM_VISIBLE_THRESHOLD);

    // In transposed layout, we simply draw a flat color.
    private boolean mDrawingFlatColor;

    private final int mEndAlpha;
    private final Paint mPaint;

    private int mCurrentScrimColor;
    private final int mTintColor;

    public AllAppsScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxScrimAlpha = Math.round(OVERVIEW.getOverviewScrimAlpha(mLauncher) * 255);
        mTintColor = Themes.getColorAccent(mContext);


        mEndAlpha = Color.alpha(mEndScrim);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Just assume the easiest UI for now, until we have the proper layout information.
        mDrawingFlatColor = true;
    }

    @Override
    public void reInitUi() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mDrawingFlatColor = dp.isVerticalBarLayout();
        updateColors();
        updateSysUiColors();
        invalidate();
    }

    @Override
    public void updateColors() {
        super.updateColors();
        if (mDrawingFlatColor) {
            return;
        }

        if (mProgress >= 1) {
            mCurrentScrimColor = 0;
        } else {
            float interpolationProgress = mScrimInterpolator.getInterpolation(mProgress);
            // Note that these ranges and interpolators are inverted because progress goes 1 to 0.
            int alpha = Math.round(Utilities.mapRange(interpolationProgress, mEndAlpha, 0));
            int color = ColorUtils.blendARGB(mEndScrim, mTintColor,
                    mProgress * TINT_DECAY_MULTIPLIER);
            mCurrentScrimColor = setColorAlphaBound(color, alpha);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawingFlatColor) {
            if (mCurrentFlatColor != 0) {
                canvas.drawColor(mCurrentFlatColor);
            }
            return;
        }

        if (Color.alpha(mCurrentScrimColor) == 0) {
            return;
        } else if (mProgress <= 0) {
            canvas.drawColor(mCurrentScrimColor);
            return;
        }

        mPaint.setColor(mCurrentScrimColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
    }
}
