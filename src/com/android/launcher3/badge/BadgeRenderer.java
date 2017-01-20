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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.graphics.IconPalette;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    public int size;
    public int textSize;

    private final RectF mBackgroundRect = new RectF();
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mTextHeight;

    public BadgeRenderer(int size, int textSize) {
        this.size = size;
        this.textSize = textSize;
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(textSize);
        // Measure the text height.
        Rect temp = new Rect();
        mTextPaint.getTextBounds("0", 0, 1, temp);
        mTextHeight = temp.height();
    }

    /**
     * Draw a circle in the top right corner of the given bounds, and draw
     * {@link BadgeInfo#getNotificationCount()} on top of the circle.
     * @param palette The colors (based on the icon) to use for the badge.
     * @param badgeInfo Contains data to draw on the badge.
     * @param iconBounds The bounds of the icon being badged.
     */
    public void draw(Canvas canvas, IconPalette palette, BadgeInfo badgeInfo, Rect iconBounds) {
        mBackgroundPaint.setColor(palette.backgroundColor);
        mTextPaint.setColor(palette.textColor);
        mBackgroundRect.set(iconBounds.right - size, iconBounds.top, iconBounds.right,
                iconBounds.top + size);
        canvas.drawOval(mBackgroundRect, mBackgroundPaint);
        String notificationCount = String.valueOf(badgeInfo.getNotificationCount());
        canvas.drawText(notificationCount,
                mBackgroundRect.centerX(),
                mBackgroundRect.centerY() + mTextHeight / 2,
                mTextPaint);
    }
}
