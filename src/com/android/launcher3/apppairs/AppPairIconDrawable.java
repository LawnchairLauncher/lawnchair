/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.apppairs;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.launcher3.icons.FastBitmapDrawable;

/**
 * A composed Drawable consisting of the two app pair icons and the background behind them (looks
 * like two rectangles).
 */
public class AppPairIconDrawable extends Drawable {
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AppPairIconDrawingParams mP;
    private final FastBitmapDrawable mIcon1;
    private final FastBitmapDrawable mIcon2;

    /**
     * Null values to use with
     * {@link Canvas#drawDoubleRoundRect(RectF, float[], RectF, float[], Paint)}, since there
     * doesn't seem to be any other API for drawing rectangles with 4 different corner radii.
     */
    private static final RectF EMPTY_RECT = new RectF();
    private static final float[] ARRAY_OF_ZEROES = new float[8];

    AppPairIconDrawable(
            AppPairIconDrawingParams p, FastBitmapDrawable icon1, FastBitmapDrawable icon2) {
        mP = p;
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(p.getBgColor());
        mIcon1 = icon1;
        mIcon2 = icon2;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mP.isLeftRightSplit()) {
            drawLeftRightSplit(canvas);
        } else {
            drawTopBottomSplit(canvas);
        }

        canvas.translate(
                mP.getStandardIconPadding() + mP.getOuterPadding(),
                mP.getStandardIconPadding() + mP.getOuterPadding()
        );

        // Draw first icon.
        canvas.save();
        // The app icons are placed differently depending on device orientation.
        if (mP.isLeftRightSplit()) {
            canvas.translate(
                    mP.getInnerPadding(),
                    mP.getBackgroundSize() / 2f - mP.getMemberIconSize() / 2f
            );
        } else {
            canvas.translate(
                    mP.getBackgroundSize() / 2f - mP.getMemberIconSize() / 2f,
                    mP.getInnerPadding()
            );
        }

        mIcon1.draw(canvas);
        canvas.restore();

        // Draw second icon.
        canvas.save();
        // The app icons are placed differently depending on device orientation.
        if (mP.isLeftRightSplit()) {
            canvas.translate(
                    mP.getBackgroundSize() - (mP.getInnerPadding() + mP.getMemberIconSize()),
                    mP.getBackgroundSize() / 2f - mP.getMemberIconSize() / 2f
            );
        } else {
            canvas.translate(
                    mP.getBackgroundSize() / 2f - mP.getMemberIconSize() / 2f,
                    mP.getBackgroundSize() - (mP.getInnerPadding() + mP.getMemberIconSize())
            );
        }

        mIcon2.draw(canvas);
        canvas.restore();
    }

    /**
     * When device is in landscape, we draw the rectangles with a left-right split.
     */
    private void drawLeftRightSplit(Canvas canvas) {
        // Get the bounds where we will draw the background image
        int width = mP.getIconSize();
        int height = mP.getIconSize();

        // The left half of the background image, excluding center channel
        RectF leftSide = new RectF(
                mP.getStandardIconPadding() + mP.getOuterPadding(),
                mP.getStandardIconPadding() + mP.getOuterPadding(),
                (width / 2f) - (mP.getCenterChannelSize() / 2f),
                height - (mP.getStandardIconPadding() + mP.getOuterPadding())
        );
        // The right half of the background image, excluding center channel
        RectF rightSide = new RectF(
                (width / 2f) + (mP.getCenterChannelSize() / 2f),
                (mP.getStandardIconPadding() + mP.getOuterPadding()),
                width - (mP.getStandardIconPadding() + mP.getOuterPadding()),
                height - (mP.getStandardIconPadding() + mP.getOuterPadding())
        );

        drawCustomRoundedRect(canvas, leftSide, new float[]{
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getBigRadius(), mP.getBigRadius()});
        drawCustomRoundedRect(canvas, rightSide, new float[]{
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getSmallRadius(), mP.getSmallRadius()});
    }

    /**
     * When device is in portrait, we draw the rectangles with a top-bottom split.
     */
    private void drawTopBottomSplit(Canvas canvas) {
        // Get the bounds where we will draw the background image
        int width = mP.getIconSize();
        int height = mP.getIconSize();

        // The top half of the background image, excluding center channel
        RectF topSide = new RectF(
                (mP.getStandardIconPadding() + mP.getOuterPadding()),
                (mP.getStandardIconPadding() + mP.getOuterPadding()),
                width - (mP.getStandardIconPadding() + mP.getOuterPadding()),
                (height / 2f) - (mP.getCenterChannelSize() / 2f)
        );
        // The bottom half of the background image, excluding center channel
        RectF bottomSide = new RectF(
                (mP.getStandardIconPadding() + mP.getOuterPadding()),
                (height / 2f) + (mP.getCenterChannelSize() / 2f),
                width - (mP.getStandardIconPadding() + mP.getOuterPadding()),
                height - (mP.getStandardIconPadding() + mP.getOuterPadding())
        );

        drawCustomRoundedRect(canvas, topSide, new float[]{
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getSmallRadius(), mP.getSmallRadius()});
        drawCustomRoundedRect(canvas, bottomSide, new float[]{
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getSmallRadius(), mP.getSmallRadius(),
                mP.getBigRadius(), mP.getBigRadius(),
                mP.getBigRadius(), mP.getBigRadius()});
    }

    /**
     * Draws a rectangle with custom rounded corners.
     * @param c The Canvas to draw on.
     * @param rect The bounds of the rectangle.
     * @param radii An array of 8 radii for the corners: top left x, top left y, top right x, top
     *              right y, bottom right x, and so on.
     */
    private void drawCustomRoundedRect(Canvas c, RectF rect, float[] radii) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Canvas.drawDoubleRoundRect is supported from Q onward
            c.drawDoubleRoundRect(rect, radii, EMPTY_RECT, ARRAY_OF_ZEROES, mBackgroundPaint);
        } else {
            // Fallback rectangle with uniform rounded corners
            c.drawRoundRect(rect, mP.getBigRadius(), mP.getBigRadius(), mBackgroundPaint);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void setAlpha(int i) {
        mBackgroundPaint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBackgroundPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicWidth() {
        return mP.getIconSize();
    }

    @Override
    public int getIntrinsicHeight() {
        return mP.getIconSize();
    }
}
