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

package com.android.wm.shell.common.split;

import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_RIGHT;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.RoundedCorner;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;

/**
 * Draws inverted rounded corners beside divider bar to keep splitting tasks cropped with proper
 * rounded corners.
 */
public class DividerRoundedCorner extends View {
    private final int mDividerWidth;
    private final Paint mDividerBarBackground;
    private final Point mStartPos = new Point();
    private InvertedRoundedCornerDrawInfo mTopLeftCorner;
    private InvertedRoundedCornerDrawInfo mTopRightCorner;
    private InvertedRoundedCornerDrawInfo mBottomLeftCorner;
    private InvertedRoundedCornerDrawInfo mBottomRightCorner;
    private boolean mIsLeftRightSplit;

    public DividerRoundedCorner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDividerWidth = getResources().getDimensionPixelSize(R.dimen.split_divider_bar_width);
        mDividerBarBackground = new Paint();
        mDividerBarBackground.setColor(
                getResources().getColor(R.color.split_divider_background, null));
        mDividerBarBackground.setFlags(Paint.ANTI_ALIAS_FLAG);
        mDividerBarBackground.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTopLeftCorner = new InvertedRoundedCornerDrawInfo(POSITION_TOP_LEFT);
        mTopRightCorner = new InvertedRoundedCornerDrawInfo(POSITION_TOP_RIGHT);
        mBottomLeftCorner = new InvertedRoundedCornerDrawInfo(POSITION_BOTTOM_LEFT);
        mBottomRightCorner = new InvertedRoundedCornerDrawInfo(POSITION_BOTTOM_RIGHT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();

        mTopLeftCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mTopLeftCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mTopRightCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mTopRightCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mBottomLeftCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mBottomLeftCorner.mPath, mDividerBarBackground);

        canvas.translate(-mStartPos.x, -mStartPos.y);
        mBottomRightCorner.calculateStartPos(mStartPos);
        canvas.translate(mStartPos.x, mStartPos.y);
        canvas.drawPath(mBottomRightCorner.mPath, mDividerBarBackground);

        canvas.restore();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    void setIsLeftRightSplit(boolean isLeftRightSplit) {
        mIsLeftRightSplit = isLeftRightSplit;
    }

    /**
     * Holds draw information of the inverted rounded corner at a specific position.
     *
     * @see {@link com.android.launcher3.taskbar.TaskbarDragLayer}
     */
    private class InvertedRoundedCornerDrawInfo {
        @RoundedCorner.Position
        private final int mCornerPosition;

        private final int mRadius;

        private final Path mPath = new Path();

        InvertedRoundedCornerDrawInfo(@RoundedCorner.Position int cornerPosition) {
            mCornerPosition = cornerPosition;

            final RoundedCorner roundedCorner = getDisplay().getRoundedCorner(cornerPosition);
            mRadius = roundedCorner == null ? 0 : roundedCorner.getRadius();

            // Starts with a filled square, and then subtracting out a circle from the appropriate
            // corner.
            final Path square = new Path();
            square.addRect(0, 0, mRadius, mRadius, Path.Direction.CW);
            final Path circle = new Path();
            circle.addCircle(
                    isLeftCorner() ? mRadius : 0 /* x */,
                    isTopCorner() ? mRadius : 0 /* y */,
                    mRadius, Path.Direction.CW);
            mPath.op(square, circle, Path.Op.DIFFERENCE);
        }

        private void calculateStartPos(Point outPos) {
            if (mIsLeftRightSplit) {
                // Place left corner at the right side of the divider bar.
                outPos.x = isLeftCorner()
                        ? getWidth() / 2 + mDividerWidth / 2
                        : getWidth() / 2 - mDividerWidth / 2 - mRadius;
                outPos.y = isTopCorner() ? 0 : getHeight() - mRadius;
            } else {
                outPos.x = isLeftCorner() ? 0 : getWidth() - mRadius;
                // Place top corner at the bottom of the divider bar.
                outPos.y = isTopCorner()
                        ? getHeight() / 2 + mDividerWidth / 2
                        : getHeight() / 2 - mDividerWidth / 2 - mRadius;
            }
        }

        private boolean isLeftCorner() {
            return mCornerPosition == POSITION_TOP_LEFT || mCornerPosition == POSITION_BOTTOM_LEFT;
        }

        private boolean isTopCorner() {
            return mCornerPosition == POSITION_TOP_LEFT || mCornerPosition == POSITION_TOP_RIGHT;
        }
    }
}
