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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    public int size;
    public int textSize;
    public IconDrawer largeIconDrawer;
    public IconDrawer smallIconDrawer;

    private final Context mContext;
    private final RectF mBackgroundRect = new RectF();
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mTextHeight;

    public BadgeRenderer(Context context) {
        mContext = context;
        Resources res = context.getResources();
        size = res.getDimensionPixelSize(R.dimen.badge_size);
        textSize = res.getDimensionPixelSize(R.dimen.badge_text_size);
        largeIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_small_padding));
        smallIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_large_padding));
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
        IconDrawer iconDrawer = badgeInfo.isIconLarge() ? largeIconDrawer : smallIconDrawer;
        Shader icon = badgeInfo.getNotificationIconForBadge(mContext, palette.backgroundColor, size,
                iconDrawer.mPadding);
        if (icon != null) {
            // Draw the notification icon with padding.
            canvas.save();
            canvas.translate(mBackgroundRect.left, mBackgroundRect.top);
            iconDrawer.drawIcon(icon, canvas);
            canvas.restore();
        } else {
            // Draw the notification count.
            String notificationCount = String.valueOf(badgeInfo.getNotificationCount());
            canvas.drawText(notificationCount,
                    mBackgroundRect.centerX(),
                    mBackgroundRect.centerY() + mTextHeight / 2,
                    mTextPaint);
        }
    }

    /** Draws the notification icon with padding of a given size. */
    private class IconDrawer {

        private final int mPadding;
        private final Bitmap mCircleClipBitmap;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
                Paint.FILTER_BITMAP_FLAG);

        public IconDrawer(int padding) {
            mPadding = padding;
            mCircleClipBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas();
            canvas.setBitmap(mCircleClipBitmap);
            canvas.drawCircle(size / 2, size / 2, size / 2 - padding, mPaint);
        }

        public void drawIcon(Shader icon, Canvas canvas) {
            mPaint.setShader(icon);
            canvas.drawBitmap(mCircleClipBitmap, 0f, 0f, mPaint);
            mPaint.setShader(null);
        }
    }
}
