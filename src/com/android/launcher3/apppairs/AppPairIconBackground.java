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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.android.launcher3.R;

/**
 * A Drawable for the background behind the twin app icons (looks like two rectangles).
 */
class AppPairIconBackground extends Drawable {
    // The underlying view that we are drawing this background on.
    private final AppPairIconGraphic icon;
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Null values to use with
     * {@link Canvas#drawDoubleRoundRect(RectF, float[], RectF, float[], Paint)}, since there
     * doesn't seem to be any other API for drawing rectangles with 4 different corner radii.
     */
    private static final RectF EMPTY_RECT = new RectF();
    private static final float[] ARRAY_OF_ZEROES = new float[8];

    AppPairIconBackground(Context context, AppPairIconGraphic iconGraphic) {
        icon = iconGraphic;
        // Set up background paint color
        TypedArray ta = context.getTheme().obtainStyledAttributes(R.styleable.FolderIconPreview);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(
                ta.getColor(R.styleable.FolderIconPreview_folderPreviewColor, 0));
        ta.recycle();
    }

    @Override
    public void draw(Canvas canvas) {
        if (icon.isLeftRightSplit()) {
            drawLeftRightSplit(canvas);
        } else {
            drawTopBottomSplit(canvas);
        }
    }

    /**
     * When device is in landscape, we draw the rectangles with a left-right split.
     */
    private void drawLeftRightSplit(Canvas canvas) {
        // Get the bounds where we will draw the background image
        int width = getBounds().width();
        int height = getBounds().height();

        // The left half of the background image, excluding center channel
        RectF leftSide = new RectF(
                0,
                0,
                (width / 2f) - (icon.getCenterChannelSize() / 2f),
                height
        );
        // The right half of the background image, excluding center channel
        RectF rightSide = new RectF(
                (width / 2f) + (icon.getCenterChannelSize() / 2f),
                0,
                width,
                height
        );

        drawCustomRoundedRect(canvas, leftSide, new float[]{
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getBigRadius(), icon.getBigRadius()});
        drawCustomRoundedRect(canvas, rightSide, new float[]{
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getSmallRadius(), icon.getSmallRadius()});
    }

    /**
     * When device is in portrait, we draw the rectangles with a top-bottom split.
     */
    private void drawTopBottomSplit(Canvas canvas) {
        // Get the bounds where we will draw the background image
        int width = getBounds().width();
        int height = getBounds().height();

        // The top half of the background image, excluding center channel
        RectF topSide = new RectF(
                0,
                0,
                width,
                (height / 2f) - (icon.getCenterChannelSize() / 2f)
        );
        // The bottom half of the background image, excluding center channel
        RectF bottomSide = new RectF(
                0,
                (height / 2f) + (icon.getCenterChannelSize() / 2f),
                width,
                height
        );

        drawCustomRoundedRect(canvas, topSide, new float[]{
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getSmallRadius(), icon.getSmallRadius()});
        drawCustomRoundedRect(canvas, bottomSide, new float[]{
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getSmallRadius(), icon.getSmallRadius(),
                icon.getBigRadius(), icon.getBigRadius(),
                icon.getBigRadius(), icon.getBigRadius()});
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
            c.drawRoundRect(rect, icon.getBigRadius(), icon.getBigRadius(), mBackgroundPaint);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void setAlpha(int i) {
        // Required by Drawable but not used.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Required by Drawable but not used.
    }
}
