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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;

/**
 * View that draws a bitmap horizontally centered. If the image width is greater than the view
 * width, the image is scaled down appropriately.
 */
public class WidgetImageView extends View {

    private final RectF mDstRectF = new RectF();
    private final int mBadgeMargin;

    private Drawable mDrawable;

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

    /** Set the drawable to use for this view. */
    public void setDrawable(Drawable drawable) {
        mDrawable = drawable;
        invalidate();
    }

    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawable != null) {
            updateDstRectF();
            mDrawable.setBounds(getBitmapBounds());
            mDrawable.draw(canvas);
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
        float bitmapWidth = mDrawable.getIntrinsicWidth();

        final float scale = bitmapWidth > myWidth ? myWidth / bitmapWidth : 1;
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = mDrawable.getIntrinsicHeight() * scale;

        mDstRectF.left = (myWidth - scaledWidth) / 2;
        mDstRectF.right = (myWidth + scaledWidth) / 2;

        if (scaledHeight > myHeight) {
            mDstRectF.top = 0;
            mDstRectF.bottom = scaledHeight;
        } else {
            mDstRectF.top = (myHeight - scaledHeight) / 2;
            mDstRectF.bottom = (myHeight + scaledHeight) / 2;
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
