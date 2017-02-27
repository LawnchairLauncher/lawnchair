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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.support.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.graphics.ShadowGenerator;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    private final Context mContext;
    private final int mSize;
    private final int mTextHeight;
    private final IconDrawer mLargeIconDrawer;
    private final IconDrawer mSmallIconDrawer;
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mBackgroundWithShadow;

    public BadgeRenderer(final Context context) {
        mContext = context;
        Resources res = context.getResources();
        mSize = res.getDimensionPixelSize(R.dimen.badge_size);
        mLargeIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_small_padding));
        mSmallIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_large_padding));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(res.getDimensionPixelSize(R.dimen.badge_text_size));
        mTextPaint.setFakeBoldText(true);
        // Measure the text height.
        Rect tempTextHeight = new Rect();
        mTextPaint.getTextBounds("0", 0, 1, tempTextHeight);
        mTextHeight = tempTextHeight.height();

        mBackgroundWithShadow = ShadowGenerator.createCircleWithShadow(Color.WHITE, mSize);
    }

    /**
     * Draw a circle in the top right corner of the given bounds, and draw
     * {@link BadgeInfo#getNotificationCount()} on top of the circle.
     * @param palette The colors (based on the icon) to use for the badge.
     * @param badgeInfo Contains data to draw on the badge. Could be null if we are animating out.
     * @param iconBounds The bounds of the icon being badged.
     * @param badgeScale The progress of the animation, from 0 to 1.
     */
    public void draw(Canvas canvas, IconPalette palette, @Nullable BadgeInfo badgeInfo,
            Rect iconBounds, float badgeScale) {
        mTextPaint.setColor(palette.textColor);
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        // We draw the badge relative to its center.
        canvas.translate(iconBounds.right - mSize / 2, iconBounds.top + mSize / 2);
        canvas.scale(badgeScale, badgeScale);
        mBackgroundPaint.setColorFilter(palette.backgroundColorMatrixFilter);
        int backgroundSize = mBackgroundWithShadow.getHeight(); // Same as width.
        canvas.drawBitmap(mBackgroundWithShadow, -backgroundSize / 2, -backgroundSize / 2,
                mBackgroundPaint);
        IconDrawer iconDrawer = badgeInfo != null && badgeInfo.isIconLarge()
                ? mLargeIconDrawer : mSmallIconDrawer;
        Shader icon = badgeInfo == null ? null : badgeInfo.getNotificationIconForBadge(
                mContext, palette.backgroundColor, mSize, iconDrawer.mPadding);
        if (icon != null) {
            // Draw the notification icon with padding.
            iconDrawer.drawIcon(icon, canvas);
        } else {
            // Draw the notification count.
            String notificationCount = badgeInfo == null ? "0"
                    : String.valueOf(badgeInfo.getNotificationCount());
            canvas.drawText(notificationCount, 0, mTextHeight / 2, mTextPaint);
        }
        canvas.restore();
    }

    /** Draws the notification icon with padding of a given size. */
    private class IconDrawer {

        private final int mPadding;
        private final Bitmap mCircleClipBitmap;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
                Paint.FILTER_BITMAP_FLAG);

        public IconDrawer(int padding) {
            mPadding = padding;
            mCircleClipBitmap = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas();
            canvas.setBitmap(mCircleClipBitmap);
            canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2 - padding, mPaint);
        }

        public void drawIcon(Shader icon, Canvas canvas) {
            mPaint.setShader(icon);
            canvas.drawBitmap(mCircleClipBitmap, -mSize / 2, -mSize / 2, mPaint);
            mPaint.setShader(null);
        }
    }
}
