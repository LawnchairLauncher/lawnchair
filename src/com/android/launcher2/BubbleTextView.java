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

import android.widget.TextView;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;

import com.android.launcher.R;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView {
    static final float CORNER_RADIUS = 8.0f;
    static final float PADDING_H = 5.0f;
    static final float PADDING_V = 1.0f;

    private final RectF mRect = new RectF();
    private Paint mPaint;

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
        setFocusable(true);
        mBackground = getBackground();
        setBackgroundDrawable(null);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(getContext().getResources().getColor(R.color.bubble_dark_background));

        final float scale = getContext().getResources().getDisplayMetrics().density;
        mCornerRadius = CORNER_RADIUS * scale;
        mPaddingH = PADDING_H * scale;
        //noinspection PointlessArithmeticExpression
        mPaddingV = PADDING_V * scale;
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

        final Layout layout = getLayout();
        final RectF rect = mRect;
        final int left = getCompoundPaddingLeft();
        final int top = getExtendedPaddingTop();

        rect.set(left + layout.getLineLeft(0) - mPaddingH,
                top + layout.getLineTop(0) -  mPaddingV,
                Math.min(left + layout.getLineRight(0) + mPaddingH, mScrollX + mRight - mLeft),
                top + layout.getLineBottom(0) + mPaddingV);
        canvas.drawRoundRect(rect, mCornerRadius, mCornerRadius, mPaint);

        super.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBackground.setCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBackground.setCallback(null);
    }
}
