/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.pageindicators;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;

import com.android.launcher3.R;

import android.graphics.drawable.Drawable;

public class CaretDrawable extends Drawable {
    public static final int LEVEL_CARET_POINTING_UP = 0; // minimum possible level value
    public static final int LEVEL_CARET_POINTING_DOWN = 10000; // maximum possible level value
    public static final int LEVEL_CARET_NEUTRAL = LEVEL_CARET_POINTING_DOWN / 2;

    private float mCaretProgress;

    private Paint mPaint = new Paint();
    private Path mPath = new Path();

    public CaretDrawable(Context context) {
        final Resources res = context.getResources();

        mPaint.setColor(res.getColor(R.color.all_apps_caret_color));
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.all_apps_caret_stroke_width));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
    }

    @Override
    public void draw(Canvas canvas) {
        if (Float.compare(mPaint.getAlpha(), 0f) == 0) {
            return;
        }

        final float width = getBounds().width() - mPaint.getStrokeWidth();
        final float height = getBounds().height() - mPaint.getStrokeWidth();
        final float left = getBounds().left + (mPaint.getStrokeWidth() / 2);
        final float top = getBounds().top + (mPaint.getStrokeWidth() / 2);

        final float verticalInset = (height / 4);
        final float caretHeight = (height - (verticalInset * 2));

        mPath.reset();
        mPath.moveTo(left, top + caretHeight * (1 - mCaretProgress));
        mPath.lineTo(left + (width / 2), top + caretHeight * mCaretProgress);
        mPath.lineTo(left + width, top + caretHeight * (1 - mCaretProgress));

        canvas.drawPath(mPath, mPaint);
    }

    @Override
    protected boolean onLevelChange(int level) {
        mCaretProgress = (float) level / (float) LEVEL_CARET_POINTING_DOWN;
        invalidateSelf();
        return true;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // no-op
    }
}
