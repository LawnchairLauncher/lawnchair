/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.AttributeSet;

import com.android.launcher.R;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends CacheableTextView {
    static final float CORNER_RADIUS = 4.0f;
    static final float SHADOW_LARGE_RADIUS = 4.0f;
    static final float SHADOW_SMALL_RADIUS = 1.75f;
    static final float SHADOW_Y_OFFSET = 2.0f;
    static final int SHADOW_LARGE_COLOUR = 0xCC000000;
    static final int SHADOW_SMALL_COLOUR = 0xBB000000;
    static final float PADDING_H = 8.0f;
    static final float PADDING_V = 3.0f;

    private final RectF mRect = new RectF();
    private Paint mPaint;
    private float mBubbleColorAlpha;
    private int mPrevAlpha = -1;

    private boolean mBackgroundSizeChanged;
    private Drawable mBackground;
    private float mCornerRadius;
    private float mPaddingH;
    private float mPaddingV;

    public BubbleTextView(Context context) {
        super(context);
        init();
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mBackground = getBackground();
        setFocusable(true);
        setBackgroundDrawable(null);

        final Resources res = getContext().getResources();
        int bubbleColor = res.getColor(R.color.bubble_dark_background);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(bubbleColor);
        mBubbleColorAlpha = Color.alpha(bubbleColor) / 255.0f;

        final float scale = res.getDisplayMetrics().density;
        mCornerRadius = CORNER_RADIUS * scale;
        mPaddingH = PADDING_H * scale;
        //noinspection PointlessArithmeticExpression
        mPaddingV = PADDING_V * scale;
    }

    protected int getCacheTopPadding() {
        return (int) PADDING_V;
    }
    protected int getCacheBottomPadding() {
        return (int) (PADDING_V + SHADOW_LARGE_RADIUS + SHADOW_Y_OFFSET);
    }
    protected int getCacheLeftPadding() {
        return (int) (PADDING_H + SHADOW_LARGE_RADIUS);
    }
    protected int getCacheRightPadding() {
        return (int) (PADDING_H + SHADOW_LARGE_RADIUS);
    }

    public void applyFromShortcutInfo(ShortcutInfo info, IconCache iconCache) {
        Bitmap b = info.getIcon(iconCache);

        setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(b),
                null, null);
        setText(info.title);
        buildAndEnableCache();
        setTag(info);

    }

    @Override
    protected boolean setFrame(int left, int top, int right, int bottom) {
        if (mLeft != left || mRight != right || mTop != top || mBottom != bottom) {
            mBackgroundSizeChanged = true;
        }
        return super.setFrame(left, top, right, bottom);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mBackground || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        Drawable d = mBackground;
        if (d != null && d.isStateful()) {
            d.setState(getDrawableState());
        }
        super.drawableStateChanged();
    }

    @Override
    public void draw(Canvas canvas) {
        if (isBuildingCache()) {
            // We enhance the shadow by drawing the shadow twice
            this.setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
            super.draw(canvas);
            this.setShadowLayer(SHADOW_SMALL_RADIUS, 0.0f, 0.0f, SHADOW_SMALL_COLOUR);
            super.draw(canvas);
        } else {
            final Drawable background = mBackground;
            if (background != null) {
                final int scrollX = mScrollX;
                final int scrollY = mScrollY;

                if (mBackgroundSizeChanged) {
                    background.setBounds(0, 0,  mRight - mLeft, mBottom - mTop);
                    mBackgroundSizeChanged = false;
                }

                if ((scrollX | scrollY) == 0) {
                    background.draw(canvas);
                } else {
                    canvas.translate(scrollX, scrollY);
                    background.draw(canvas);
                    canvas.translate(-scrollX, -scrollY);
                }
            }

            super.draw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBackground != null) mBackground.setCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground != null) mBackground.setCallback(null);
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (mPrevAlpha != alpha) {
            mPrevAlpha = alpha;
            mPaint.setAlpha((int) (alpha * mBubbleColorAlpha));
            super.onSetAlpha(alpha);
        }
        return true;
    }
}
