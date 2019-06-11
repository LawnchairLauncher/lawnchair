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
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import ch.deletescape.lawnchair.LawnchairUtilsKt;
import com.android.launcher3.graphics.ShadowGenerator;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    private static final String TAG = "BadgeRenderer";

    // The badge sizes are defined as percentages of the app icon size.
    private static final float SIZE_PERCENTAGE = 0.38f;
    private static final float SIZE_PERCENTAGE_WITH_COUNT = 0.58f;

    // Extra scale down of the dot
    private static final float DOT_SCALE = 0.6f;

    // Used to expand the width of the badge for each additional digit.
    private static final float OFFSET_PERCENTAGE = 0.02f;
    private static final float OFFSET_PERCENTAGE_WITH_COUNT = 0.15f;

    private static final int MAX_COUNT = 99;

    private static final boolean DEBUG_CENTER = false;

    private final float mDotCenterOffset;
    private final int mOffset;
    private final float mCircleRadius;
    private final Paint mCirclePaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);
    private final Paint mTextPaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

    private final Bitmap mBackgroundWithShadow;
    private final float mBitmapOffset;
    private final int mSize;

    private Rect mTmp = new Rect();
    private final boolean mDisplayCount;

    public BadgeRenderer(int iconSizePx, boolean displayCount) {
        mDisplayCount = displayCount;
        mDotCenterOffset =
                (displayCount ? SIZE_PERCENTAGE_WITH_COUNT : SIZE_PERCENTAGE) * iconSizePx;
        mOffset = (int) ((displayCount ? OFFSET_PERCENTAGE_WITH_COUNT : OFFSET_PERCENTAGE)
                * iconSizePx);

        mSize = (int) (DOT_SCALE * mDotCenterOffset);
        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        mBackgroundWithShadow = builder.setupBlurForSize(mSize).createPill(mSize, mSize);
        mCircleRadius = builder.radius;

        mBitmapOffset = -mBackgroundWithShadow.getHeight() * 0.5f; // Same as width.

        mTextPaint.setTextSize(mSize * 0.7f);
        mTextPaint.setTextAlign(Align.LEFT);
    }

    /**
     * Draw a circle in the top right corner of the given bounds, and draw
     * {@link BadgeInfo#getNotificationCount()} on top of the circle.
     * @param color The color (based on the icon) to use for the badge.
     * @param iconBounds The bounds of the icon being badged.
     * @param badgeScale The progress of the animation, from 0 to 1.
     * @param spaceForOffset How much space is available to offset the badge up and to the right.
     */
    public void draw(Canvas canvas, int color, Rect iconBounds, float badgeScale,
            Point spaceForOffset, int numNotifications) {
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

        // TODO: Add an option to change the text font?
        if (mDisplayCount && numNotifications > 0) {
            mTextPaint.setColor(LawnchairUtilsKt.getForegroundColor(color));
            String text = String.valueOf(Math.min(numNotifications, MAX_COUNT));
            mTextPaint.getTextBounds(text, 0, text.length(), mTmp);
            float x = (-mTmp.width() / 2f - mTmp.left) * getAdjustment(numNotifications);
            float y = mTmp.height() / 2f - mTmp.bottom;
            canvas.drawText(text, x, y, mTextPaint);
            if (DEBUG_CENTER) {
                Paint linePaint = new Paint();
                linePaint.setColor(Color.YELLOW);
                linePaint.setStrokeWidth(2f);
                canvas.drawLine(0, -mSize / 2, 0, mSize / 2, linePaint);
            }
        }

        canvas.restore();
    }

    /**
     * An attempt to adjust digits to their perceived center, they were tuned with Roboto but should
     * (hopefully) work with other OEM fonts as well
     *
     * I am probably insane
     */
    private float getAdjustment(int number) {
        switch (number) {
            case 1:
                return 1.01f;
            case 2:
                return 0.99f;
            case 3:
                return 0.98f;
            case 4:
                return 0.98f;
            case 6:
                return 0.98f;
            case 7:
                return 1.02f;
            case 9:
                return 0.9f;
        }
        return 1f;
    }
}
