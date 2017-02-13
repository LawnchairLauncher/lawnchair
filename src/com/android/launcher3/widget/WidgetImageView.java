/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * View that draws a bitmap horizontally centered. If the image width is greater than the view
 * width, the image is scaled down appropriately.
 */
public class WidgetImageView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF mDstRectF = new RectF();
    private final int mBadgeMargin;

    private Bitmap mBitmap;
    private Drawable mBadge;

    public WidgetImageView(Context context) {
        this(context, null);
    }

    public WidgetImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mBadgeMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.profile_badge_margin);
    }

    public void setBitmap(Bitmap bitmap, Drawable badge) {
        mBitmap = bitmap;
        mBadge = badge;
        invalidate();
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            updateDstRectF();
            canvas.drawBitmap(mBitmap, null, mDstRectF, mPaint);

            // Only draw the badge if a preview was drawn.
            if (mBadge != null) {
                mBadge.draw(canvas);
            }
        }
    }

    /**
     * Prevents the inefficient alpha view rendering.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void updateDstRectF() {
        float myWidth = getWidth();
        float myHeight = getHeight();
        float bitmapWidth = mBitmap.getWidth();

        final float scale = bitmapWidth > myWidth ? myWidth / bitmapWidth : 1;
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = mBitmap.getHeight() * scale;

        mDstRectF.left = (myWidth - scaledWidth) / 2;
        mDstRectF.right = (myWidth + scaledWidth) / 2;

        if (scaledHeight > myHeight) {
            mDstRectF.top = 0;
            mDstRectF.bottom = scaledHeight;
        } else {
            mDstRectF.top = (myHeight - scaledHeight) / 2;
            mDstRectF.bottom = (myHeight + scaledHeight) / 2;
        }

        if (mBadge != null) {
            Rect bounds = mBadge.getBounds();
            int left = Utilities.boundToRange(
                    (int) (mDstRectF.right + mBadgeMargin - bounds.width()),
                    mBadgeMargin, getWidth() - bounds.width());
            int top = Utilities.boundToRange(
                    (int) (mDstRectF.bottom + mBadgeMargin - bounds.height()),
                    mBadgeMargin, getHeight() - bounds.height());
            mBadge.setBounds(left, top, bounds.width() + left, bounds.height() + top);
        }
    }

    /**
     * @return the bounds where the image was drawn.
     */
    public Rect getBitmapBounds() {
        updateDstRectF();
        Rect rect = new Rect();
        mDstRectF.round(rect);
        return rect;
    }
}
