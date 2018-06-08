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
package com.android.quickstep.views;

import static android.support.v4.graphics.ColorUtils.compositeColors;
import static android.support.v4.graphics.ColorUtils.setAlphaComponent;

import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.util.AttributeSet;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;

/**
 * Scrim used for all-apps and shelf in Overview
 * In transposed layout, it behaves as a simple color scrim.
 * In portrait layout, it draws a rounded rect such that
 *    From normal state to overview state, the shelf just fades in and does not move
 *    From overview state to all-apps state the shelf moves up and fades in to cover the screen
 */
public class ShelfScrimView extends ScrimView {

    // In transposed layout, we simply draw a flat color.
    private boolean mDrawingFlatColor;

    // For shelf mode
    private final int mEndAlpha;
    private final int mThresholdAlpha;
    private final float mRadius;
    private final float mMaxScrimAlpha;
    private final Paint mPaint;

    // Max vertical progress after which the scrim stops moving.
    private float mMoveThreshold;
    // Minimum visible size of the scrim.
    private int mMinSize;

    private float mScrimMoveFactor = 0;
    private int mShelfColor;
    private int mRemainingScreenColor;

    private final Path mTempPath = new Path();
    private final Path mRemainingScreenPath = new Path();
    private boolean mRemainingScreenPathValid = false;

    public ShelfScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxScrimAlpha = OVERVIEW.getWorkspaceScrimAlpha(mLauncher);

        mEndAlpha = Color.alpha(mEndScrim);
        mThresholdAlpha = Themes.getAttrInteger(context, R.attr.allAppsInterimScrimAlpha);
        mRadius = mLauncher.getResources().getDimension(R.dimen.shelf_surface_radius);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Just assume the easiest UI for now, until we have the proper layout information.
        mDrawingFlatColor = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRemainingScreenPathValid = false;
    }

    @Override
    public void reInitUi() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mDrawingFlatColor = dp.isVerticalBarLayout();

        if (!mDrawingFlatColor) {
            float swipeLength = OverviewState.getDefaultSwipeHeight(mLauncher);
            mMoveThreshold = 1 - swipeLength / mLauncher.getAllAppsController().getShiftRange();
            mMinSize = dp.hotseatBarSizePx + dp.getInsets().bottom;
            mRemainingScreenPathValid = false;
            updateColors();
        }
        updateDragHandleAlpha();
        invalidate();
    }

    @Override
    public void updateColors() {
        super.updateColors();
        if (mDrawingFlatColor) {
            return;
        }

        if (mProgress >= mMoveThreshold) {
            mScrimMoveFactor = 1;

            if (mProgress >= 1) {
                mShelfColor = 0;
            } else {
                int alpha = Math.round(mThresholdAlpha * ACCEL_2.getInterpolation(
                        (1 - mProgress) / (1 - mMoveThreshold)));
                mShelfColor = setAlphaComponent(mEndScrim, alpha);
            }

            mRemainingScreenColor = 0;
        } else if (mProgress <= 0) {
            mScrimMoveFactor = 0;
            mShelfColor = mCurrentFlatColor;
            mRemainingScreenColor = 0;

        } else {
            mScrimMoveFactor = mProgress / mMoveThreshold;
            mRemainingScreenColor = setAlphaComponent(mScrimColor,
                    Math.round((1 - mScrimMoveFactor) * mMaxScrimAlpha * 255));

            // Merge the remainingScreenColor and shelfColor in one to avoid overdraw.
            int alpha = mEndAlpha - Math.round((mEndAlpha - mThresholdAlpha) * mScrimMoveFactor);
            mShelfColor = compositeColors(setAlphaComponent(mEndScrim, alpha),
                    mRemainingScreenColor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float translate = drawBackground(canvas);

        if (mDragHandle != null) {
            canvas.translate(0, -translate);
            mDragHandle.draw(canvas);
            canvas.translate(0, translate);
        }
    }

    private float drawBackground(Canvas canvas) {
        if (mDrawingFlatColor) {
            if (mCurrentFlatColor != 0) {
                canvas.drawColor(mCurrentFlatColor);
            }
            return 0;
        }

        if (mShelfColor == 0) {
            return 0;
        } else if (mScrimMoveFactor <= 0) {
            canvas.drawColor(mShelfColor);
            return getHeight();
        }

        float minTop = getHeight() - mMinSize;
        float top = minTop * mScrimMoveFactor - mDragHandleSize;

        // Draw the scrim over the remaining screen if needed.
        if (mRemainingScreenColor != 0) {
            if (!mRemainingScreenPathValid) {
                mTempPath.reset();
                // Using a arbitrary '+10' in the bottom to avoid any left-overs at the
                // corners due to rounding issues.
                mTempPath.addRoundRect(0, minTop, getWidth(), getHeight() + mRadius + 10,
                        mRadius, mRadius, Direction.CW);

                mRemainingScreenPath.reset();
                mRemainingScreenPath.addRect(0, 0, getWidth(), getHeight(), Direction.CW);
                mRemainingScreenPath.op(mTempPath, Op.DIFFERENCE);
            }

            float offset = minTop - top;
            canvas.translate(0, -offset);
            mPaint.setColor(mRemainingScreenColor);
            canvas.drawPath(mRemainingScreenPath, mPaint);
            canvas.translate(0, offset);
        }

        mPaint.setColor(mShelfColor);
        canvas.drawRoundRect(0, top, getWidth(), getHeight() + mRadius,
                mRadius, mRadius, mPaint);
        return minTop - mDragHandleSize - top;
    }
}
