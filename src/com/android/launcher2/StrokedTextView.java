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

package com.android.launcher2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher.R;

/**
 * This class adds a stroke to the generic TextView allowing the text to stand out better against
 * the background (ie. in the AllApps button).
 */
public class StrokedTextView extends TextView {
    private final Canvas mCanvas = new Canvas();
    private final Paint mPaint = new Paint();
    private Bitmap mCache;
    private boolean mUpdateCachedBitmap;
    private int mStrokeColor;
    private float mStrokeWidth;
    private int mTextColor;

    public StrokedTextView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public StrokedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public StrokedTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StrokedTextView,
                defStyle, 0);
        mStrokeColor = a.getColor(R.styleable.StrokedTextView_strokeColor, 0xFF000000);
        mStrokeWidth = a.getFloat(R.styleable.StrokedTextView_strokeWidth, 0.0f);
        mTextColor = a.getColor(R.styleable.StrokedTextView_strokeTextColor, 0xFFFFFFFF);
        a.recycle();
        mUpdateCachedBitmap = true;

        // Setup the text paint
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        mUpdateCachedBitmap = true;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mUpdateCachedBitmap = true;
            mCache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } else {
            mCache = null;
        }
    }

    protected void onDraw(Canvas canvas) {
        if (mCache != null) {
            if (mUpdateCachedBitmap) {
                final int w = getMeasuredWidth();
                final int h = getMeasuredHeight();
                final String text = getText().toString();
                final Rect textBounds = new Rect();
                final Paint textPaint = getPaint();
                final int textWidth = (int) textPaint.measureText(text);
                textPaint.getTextBounds("x", 0, 1, textBounds);

                // Clear the old cached image
                mCanvas.setBitmap(mCache);
                mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

                // Draw the drawable
                final int drawableLeft = getPaddingLeft();
                final int drawableTop = getPaddingTop();
                final Drawable[] drawables = getCompoundDrawables();
                for (int i = 0; i < drawables.length; ++i) {
                    if (drawables[i] != null) {
                        drawables[i].setBounds(drawableLeft, drawableTop,
                                drawableLeft + drawables[i].getIntrinsicWidth(),
                                drawableTop + drawables[i].getIntrinsicHeight());
                        drawables[i].draw(mCanvas);
                    }
                }

                final int left = w - getPaddingRight() - textWidth;
                final int bottom = (h + textBounds.height()) / 2;

                // Draw the outline of the text
                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setColor(mStrokeColor);
                mPaint.setTextSize(getTextSize());
                mCanvas.drawText(text, left, bottom, mPaint);

                // Draw the text itself
                mPaint.setStrokeWidth(0);
                mPaint.setColor(mTextColor);
                mCanvas.drawText(text, left, bottom, mPaint);

                mUpdateCachedBitmap = false;
            }
            canvas.drawBitmap(mCache, 0, 0, mPaint);
        } else {
            super.onDraw(canvas);
        }
    }
}
