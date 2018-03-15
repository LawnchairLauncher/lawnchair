/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.badge;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import com.android.launcher3.graphics.ShadowGenerator;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    private static final String TAG = "BadgeRenderer";

    // The badge sizes are defined as percentages of the app icon size.
    private static final float SIZE_PERCENTAGE = 0.38f;

    // Extra scale down of the dot
    private static final float DOT_SCALE = 0.6f;

    // Used to expand the width of the badge for each additional digit.
    private static final float OFFSET_PERCENTAGE = 0.02f;

    private final float mDotCenterOffset;
    private final int mOffset;
    private final float mCircleRadius;
    private final Paint mCirclePaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

    private final Bitmap mBackgroundWithShadow;
    private final float mBitmapOffset;

    public BadgeRenderer(int iconSizePx) {
        mDotCenterOffset = SIZE_PERCENTAGE * iconSizePx;
        mOffset = (int) (OFFSET_PERCENTAGE * iconSizePx);

        int size = (int) (DOT_SCALE * mDotCenterOffset);
        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        mBackgroundWithShadow = builder.setupBlurForSize(size).createPill(size, size);
        mCircleRadius = builder.radius;

        mBitmapOffset = -mBackgroundWithShadow.getHeight() * 0.5f; // Same as width.
    }

    /**
     * Draw a circle in the top right corner of the given bounds, and draw
     * {@link BadgeInfo#getNotificationCount()} on top of the circle.
     * @param color The color (based on the icon) to use for the badge.
     * @param iconBounds The bounds of the icon being badged.
     * @param badgeScale The progress of the animation, from 0 to 1.
     * @param spaceForOffset How much space is available to offset the badge up and to the right.
     */
    public void draw(
            Canvas canvas, int color, Rect iconBounds, float badgeScale, Point spaceForOffset) {
        if (iconBounds == null || spaceForOffset == null) {
            Log.e(TAG, "Invalid null argument(s) passed in call to draw.");
            return;
        }
        canvas.save();
        // We draw the badge relative to its center.
        float badgeCenterX = iconBounds.right - mDotCenterOffset / 2;
        float badgeCenterY = iconBounds.top + mDotCenterOffset / 2;

        int offsetX = Math.min(mOffset, spaceForOffset.x);
        int offsetY = Math.min(mOffset, spaceForOffset.y);
        canvas.translate(badgeCenterX + offsetX, badgeCenterY - offsetY);
        canvas.scale(badgeScale, badgeScale);

        mCirclePaint.setColor(Color.BLACK);
        canvas.drawBitmap(mBackgroundWithShadow, mBitmapOffset, mBitmapOffset, mCirclePaint);
        mCirclePaint.setColor(color);
        canvas.drawCircle(0, 0, mCircleRadius, mCirclePaint);
        canvas.restore();
    }
}
