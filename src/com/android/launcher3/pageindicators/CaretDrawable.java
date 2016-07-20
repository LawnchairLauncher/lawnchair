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

    private Paint mShadowPaint = new Paint();
    private Paint mCaretPaint = new Paint();
    private Path mPath = new Path();

    public CaretDrawable(Context context) {
        final Resources res = context.getResources();

        final int strokeWidth = res.getDimensionPixelSize(R.dimen.all_apps_caret_stroke_width);
        final int shadowSpread = res.getDimensionPixelSize(R.dimen.all_apps_caret_shadow_spread);

        mCaretPaint.setColor(res.getColor(R.color.all_apps_caret_color));
        mCaretPaint.setAntiAlias(true);
        mCaretPaint.setStrokeWidth(strokeWidth);
        mCaretPaint.setStyle(Paint.Style.STROKE);
        mCaretPaint.setStrokeCap(Paint.Cap.SQUARE);
        mCaretPaint.setStrokeJoin(Paint.Join.MITER);

        mShadowPaint.setColor(res.getColor(R.color.all_apps_caret_shadow_color));
        mShadowPaint.setAntiAlias(true);
        mShadowPaint.setStrokeWidth(strokeWidth + (shadowSpread * 2));
        mShadowPaint.setStyle(Paint.Style.STROKE);
        mShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        mShadowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        // Assumes caret paint is more important than shadow paint
        if (Float.compare(mCaretPaint.getAlpha(), 0f) == 0) {
            return;
        }

        // Assumes shadow stroke width is larger
        final float width = getBounds().width() - mShadowPaint.getStrokeWidth();
        final float height = getBounds().height() - mShadowPaint.getStrokeWidth();
        final float left = getBounds().left + (mShadowPaint.getStrokeWidth() / 2);
        final float top = getBounds().top + (mShadowPaint.getStrokeWidth() / 2);

        final float verticalInset = (height / 4);
        final float caretHeight = (height - (verticalInset * 2));

        mPath.reset();
        mPath.moveTo(left, top + caretHeight * (1 - mCaretProgress));
        mPath.lineTo(left + (width / 2), top + caretHeight * mCaretProgress);
        mPath.lineTo(left + width, top + caretHeight * (1 - mCaretProgress));

        canvas.drawPath(mPath, mShadowPaint);
        canvas.drawPath(mPath, mCaretPaint);
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
        mCaretPaint.setAlpha(alpha);
        mShadowPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // no-op
    }
}
