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

import static android.support.v4.graphics.ColorUtils.setAlphaComponent;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;

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
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
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

    // If the progress is more than this, shelf follows the finger, otherwise it moves faster to
    // cover the whole screen
    private static final float SCRIM_CATCHUP_THRESHOLD = 0.2f;

    // In transposed layout, we simply draw a flat color.
    private boolean mDrawingFlatColor;

    // For shelf mode
    private final int mEndAlpha;
    private final float mRadius;
    private final int mMaxScrimAlpha;
    private final Paint mPaint;

    // Mid point where the alpha changes
    private int mMidAlpha;
    private float mMidProgress;

    private float mShiftRange;

    private final float mShelfOffset;
    private float mTopOffset;
    private float mShelfTop;
    private float mShelfTopAtThreshold;

    private int mShelfColor;
    private int mRemainingScreenColor;

    private final Path mTempPath = new Path();
    private final Path mRemainingScreenPath = new Path();
    private boolean mRemainingScreenPathValid = false;

    public ShelfScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxScrimAlpha = Math.round(OVERVIEW.getWorkspaceScrimAlpha(mLauncher) * 255);

        mEndAlpha = Color.alpha(mEndScrim);
        mRadius = mLauncher.getResources().getDimension(R.dimen.shelf_surface_radius);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mShelfOffset = context.getResources().getDimension(R.dimen.shelf_surface_offset);
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
            mRemainingScreenPathValid = false;
            mShiftRange = mLauncher.getAllAppsController().getShiftRange();

            mMidProgress = OVERVIEW.getVerticalProgress(mLauncher);
            mMidAlpha = mMidProgress >= 1 ? 0
                    : Themes.getAttrInteger(getContext(), R.attr.allAppsInterimScrimAlpha);

            mTopOffset = dp.getInsets().top - mShelfOffset;
            mShelfTopAtThreshold = mShiftRange * SCRIM_CATCHUP_THRESHOLD + mTopOffset;
            updateColors();
        }
        updateDragHandleAlpha();
        invalidate();
    }

    @Override
    public void updateColors() {
        super.updateColors();
        if (mDrawingFlatColor) {
            mDragHandleOffset = 0;
            return;
        }

        mDragHandleOffset = mShelfOffset - mDragHandleSize;
        if (mProgress >= SCRIM_CATCHUP_THRESHOLD) {
            mShelfTop = mShiftRange * mProgress + mTopOffset;
        } else {
            mShelfTop = Utilities.mapRange(mProgress / SCRIM_CATCHUP_THRESHOLD, -mRadius,
                    mShelfTopAtThreshold);
        }

        if (mProgress >= 1) {
            mRemainingScreenColor = 0;
            mShelfColor = 0;
        } else if (mProgress >= mMidProgress) {
            mRemainingScreenColor = 0;

            int alpha = Math.round(Utilities.mapToRange(
                    mProgress, mMidProgress, 1, mMidAlpha, 0, ACCEL));
            mShelfColor = setAlphaComponent(mEndScrim, alpha);
        } else {
            mDragHandleOffset += mShiftRange * (mMidProgress - mProgress);

            // Note that these ranges and interpolators are inverted because progress goes 1 to 0.
            int alpha = Math.round(
                    Utilities.mapToRange(mProgress, (float) 0, mMidProgress, (float) mEndAlpha,
                            (float) mMidAlpha, Interpolators.clampToProgress(ACCEL, 0.5f, 1f)));
            mShelfColor = setAlphaComponent(mEndScrim, alpha);

            int remainingScrimAlpha = Math.round(
                    Utilities.mapToRange(mProgress, (float) 0, mMidProgress, mMaxScrimAlpha,
                            (float) 0, LINEAR));
            mRemainingScreenColor = setAlphaComponent(mScrimColor, remainingScrimAlpha);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawDragHandle(canvas);
    }

    private void drawBackground(Canvas canvas) {
        if (mDrawingFlatColor) {
            if (mCurrentFlatColor != 0) {
                canvas.drawColor(mCurrentFlatColor);
            }
            return;
        }

        if (Color.alpha(mShelfColor) == 0) {
            return;
        } else if (mProgress <= 0) {
            canvas.drawColor(mShelfColor);
            return;
        }

        int height = getHeight();
        int width = getWidth();
        // Draw the scrim over the remaining screen if needed.
        if (mRemainingScreenColor != 0) {
            if (!mRemainingScreenPathValid) {
                mTempPath.reset();
                // Using a arbitrary '+10' in the bottom to avoid any left-overs at the
                // corners due to rounding issues.
                mTempPath.addRoundRect(0, height - mRadius, width, height + mRadius + 10,
                        mRadius, mRadius, Direction.CW);
                mRemainingScreenPath.reset();
                mRemainingScreenPath.addRect(0, 0, width, height, Direction.CW);
                mRemainingScreenPath.op(mTempPath, Op.DIFFERENCE);
            }

            float offset = height - mRadius - mShelfTop;
            canvas.translate(0, -offset);
            mPaint.setColor(mRemainingScreenColor);
            canvas.drawPath(mRemainingScreenPath, mPaint);
            canvas.translate(0, offset);
        }

        mPaint.setColor(mShelfColor);
        canvas.drawRoundRect(0, mShelfTop, width, height + mRadius, mRadius, mRadius, mPaint);
    }
}
