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

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.graphics.ViewScrim;
import com.android.launcher3.util.Themes;

/**
 * Scrim used for all-apps and shelf in Overview
 * In transposed layout, it behaves as a simple color scrim.
 * In portrait layout, it draws a rounded rect such that
 *    From normal state to overview state, the shelf just fades in and does not move
 *    From overview state to all-apps state the self moves up and fades in to cover the screen
 */
public class AllAppsScrim extends ViewScrim<AllAppsContainerView> {

    private static final int THRESHOLD_ALPHA_DARK = 102;
    private static final int THRESHOLD_ALPHA_LIGHT = 46;

    private final Launcher mLauncher;
    private final int mEndColor;

    private int mProgressColor;

    // In transposed layout, we simply draw a flat color.
    private boolean mDrawingFlatColor;

    private final Paint mVerticalPaint;
    private float mVerticalProgress;

    private final int mEndAlpha;
    private final int mThresholdAlpha;
    private final float mRadius;
    private final float mTopPadding;

    // Max vertical progress after which the scrim stops moving.
    private float mMoveThreshold;
    // Minimum visible size of the scrim.
    private int mMinSize;
    private float mDrawFactor = 0;

    public AllAppsScrim(AllAppsContainerView view) {
        super(view);
        mLauncher = Launcher.getLauncher(view.getContext());
        mEndColor = Themes.getAttrColor(mLauncher, R.attr.allAppsScrimColor);
        mProgressColor = mEndColor;

        mEndAlpha = Color.alpha(mEndColor);
        mThresholdAlpha = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark)
                ? THRESHOLD_ALPHA_DARK : THRESHOLD_ALPHA_LIGHT;
        mRadius = mLauncher.getResources().getDimension(R.dimen.shelf_surface_radius);
        mTopPadding = mLauncher.getResources().getDimension(R.dimen.shelf_surface_top_padding);

        mVerticalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mVerticalPaint.setColor(ColorUtils.setAlphaComponent(mEndColor, 255));

        // Just assume the easiest UI for now, until we have the proper layout information.
        mDrawingFlatColor = true;
    }

    @Override
    protected void onProgressChanged() {
        mProgressColor = ColorUtils.setAlphaComponent(mEndColor,
                Math.round(DEACCEL_2.getInterpolation(mProgress) * Color.alpha(mEndColor)));
    }

    @Override
    public void draw(Canvas canvas, int width, int height) {
        if (mDrawingFlatColor) {
            if (mProgress > 0) {
                canvas.drawColor(mProgressColor);
            }
            return;
        }

        if (mVerticalPaint.getAlpha() == 0) {
            return;
        } else if (mDrawFactor <= 0) {
            canvas.drawPaint(mVerticalPaint);
        } else {
            float top = (height - mMinSize) * mDrawFactor - mTopPadding;
            canvas.drawRoundRect(0, top - mRadius, width, height + mRadius,
                    mRadius, mRadius, mVerticalPaint);
        }
    }

    public void reInitUi() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mDrawingFlatColor = dp.isVerticalBarLayout();

        if (!mDrawingFlatColor) {
            float swipeLength = OverviewState.getDefaultSwipeHeight(mLauncher);
            mMoveThreshold = 1 - swipeLength / mLauncher.getAllAppsController().getShiftRange();
            mMinSize = dp.hotseatBarSizePx + dp.getInsets().bottom;
            onVerticalProgress(mVerticalProgress);
        }
        invalidate();
    }

    public void onVerticalProgress(float progress) {
        mVerticalProgress = progress;
        if (mDrawingFlatColor) {
            return;
        }

        float drawFactor;
        int alpha;
        if (mVerticalProgress >= mMoveThreshold) {
            drawFactor = 1;
            alpha = mVerticalProgress >= 1 ? 0 : Math.round(mThresholdAlpha
                    * ACCEL_2.getInterpolation((1 - mVerticalProgress) / (1 - mMoveThreshold)));
        } else if (mVerticalProgress <= 0) {
            drawFactor = 0;
            alpha = mEndAlpha;
        } else {
            drawFactor = mVerticalProgress / mMoveThreshold;
            alpha = mEndAlpha - Math.round((mEndAlpha - mThresholdAlpha) * drawFactor);
        }
        alpha = Utilities.boundToRange(alpha, 0, 255);
        if (alpha != mVerticalPaint.getAlpha() || drawFactor != mDrawFactor) {
            mVerticalPaint.setAlpha(alpha);
            mDrawFactor = drawFactor;
            invalidate();
        }
    }

}
