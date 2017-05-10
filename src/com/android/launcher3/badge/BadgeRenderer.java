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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.graphics.ShadowGenerator;

/**
 * Contains parameters necessary to draw a badge for an icon (e.g. the size of the badge).
 * @see BadgeInfo for the data to draw
 */
public class BadgeRenderer {

    private static final boolean DOTS_ONLY = true;

    // The badge sizes are defined as percentages of the app icon size.
    private static final float SIZE_PERCENTAGE = 0.38f;
    // Used to expand the width of the badge for each additional digit.
    private static final float CHAR_SIZE_PERCENTAGE = 0.12f;
    private static final float TEXT_SIZE_PERCENTAGE = 0.26f;
    private static final float OFFSET_PERCENTAGE = 0.02f;
    private static final float STACK_OFFSET_PERCENTAGE_X = 0.05f;
    private static final float STACK_OFFSET_PERCENTAGE_Y = 0.06f;
    private static final float DOT_SCALE = 0.6f;

    private final Context mContext;
    private final int mSize;
    private final int mCharSize;
    private final int mTextHeight;
    private final int mOffset;
    private final int mStackOffsetX;
    private final int mStackOffsetY;
    private final IconDrawer mLargeIconDrawer;
    private final IconDrawer mSmallIconDrawer;
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final SparseArray<Bitmap> mBackgroundsWithShadow;

    public BadgeRenderer(Context context, int iconSizePx) {
        mContext = context;
        Resources res = context.getResources();
        mSize = (int) (SIZE_PERCENTAGE * iconSizePx);
        mCharSize = (int) (CHAR_SIZE_PERCENTAGE * iconSizePx);
        mOffset = (int) (OFFSET_PERCENTAGE * iconSizePx);
        mStackOffsetX = (int) (STACK_OFFSET_PERCENTAGE_X * iconSizePx);
        mStackOffsetY = (int) (STACK_OFFSET_PERCENTAGE_Y * iconSizePx);
        mTextPaint.setTextSize(iconSizePx * TEXT_SIZE_PERCENTAGE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mLargeIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_small_padding));
        mSmallIconDrawer = new IconDrawer(res.getDimensionPixelSize(R.dimen.badge_large_padding));
        // Measure the text height.
        Rect tempTextHeight = new Rect();
        mTextPaint.getTextBounds("0", 0, 1, tempTextHeight);
        mTextHeight = tempTextHeight.height();

        mBackgroundsWithShadow = new SparseArray<>(3);
    }

    /**
     * Draw a circle in the top right corner of the given bounds, and draw
     * {@link BadgeInfo#getNotificationCount()} on top of the circle.
     * @param palette The colors (based on the icon) to use for the badge.
     * @param badgeInfo Contains data to draw on the badge. Could be null if we are animating out.
     * @param iconBounds The bounds of the icon being badged.
     * @param badgeScale The progress of the animation, from 0 to 1.
     * @param spaceForOffset How much space is available to offset the badge up and to the right.
     */
    public void draw(Canvas canvas, IconPalette palette, @Nullable BadgeInfo badgeInfo,
            Rect iconBounds, float badgeScale, Point spaceForOffset) {
        mTextPaint.setColor(palette.textColor);
        IconDrawer iconDrawer = badgeInfo != null && badgeInfo.isIconLarge()
                ? mLargeIconDrawer : mSmallIconDrawer;
        Shader icon = badgeInfo == null ? null : badgeInfo.getNotificationIconForBadge(
                mContext, palette.backgroundColor, mSize, iconDrawer.mPadding);
        String notificationCount = badgeInfo == null ? "0"
                : String.valueOf(badgeInfo.getNotificationCount());
        int numChars = notificationCount.length();
        int width = DOTS_ONLY ? mSize : mSize + mCharSize * (numChars - 1);
        // Lazily load the background with shadow.
        Bitmap backgroundWithShadow = mBackgroundsWithShadow.get(numChars);
        if (backgroundWithShadow == null) {
            backgroundWithShadow = ShadowGenerator.createPillWithShadow(Color.WHITE, width, mSize);
            mBackgroundsWithShadow.put(numChars, backgroundWithShadow);
        }
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        // We draw the badge relative to its center.
        int badgeCenterX = iconBounds.right - width / 2;
        int badgeCenterY = iconBounds.top + mSize / 2;
        boolean isText = !DOTS_ONLY && badgeInfo != null && badgeInfo.getNotificationCount() != 0;
        boolean isIcon = !DOTS_ONLY && icon != null;
        boolean isDot = !(isText || isIcon);
        if (isDot) {
            badgeScale *= DOT_SCALE;
        }
        int offsetX = Math.min(mOffset, spaceForOffset.x);
        int offsetY = Math.min(mOffset, spaceForOffset.y);
        canvas.translate(badgeCenterX + offsetX, badgeCenterY - offsetY);
        canvas.scale(badgeScale, badgeScale);
        // Prepare the background and shadow and possible stacking effect.
        mBackgroundPaint.setColorFilter(palette.backgroundColorMatrixFilter);
        int backgroundWithShadowSize = backgroundWithShadow.getHeight(); // Same as width.
        boolean shouldStack = !isDot && badgeInfo != null
                && badgeInfo.getNotificationKeys().size() > 1;
        if (shouldStack) {
            int offsetDiffX = mStackOffsetX - mOffset;
            int offsetDiffY = mStackOffsetY - mOffset;
            canvas.translate(offsetDiffX, offsetDiffY);
            canvas.drawBitmap(backgroundWithShadow, -backgroundWithShadowSize / 2,
                    -backgroundWithShadowSize / 2, mBackgroundPaint);
            canvas.translate(-offsetDiffX, -offsetDiffY);
        }

        if (isText) {
            canvas.drawBitmap(backgroundWithShadow, -backgroundWithShadowSize / 2,
                    -backgroundWithShadowSize / 2, mBackgroundPaint);
            canvas.drawText(notificationCount, 0, mTextHeight / 2, mTextPaint);
        } else if (isIcon) {
            canvas.drawBitmap(backgroundWithShadow, -backgroundWithShadowSize / 2,
                    -backgroundWithShadowSize / 2, mBackgroundPaint);
            iconDrawer.drawIcon(icon, canvas);
        } else if (isDot) {
            mBackgroundPaint.setColorFilter(palette.saturatedBackgroundColorMatrixFilter);
            canvas.drawBitmap(backgroundWithShadow, -backgroundWithShadowSize / 2,
                    -backgroundWithShadowSize / 2, mBackgroundPaint);
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
