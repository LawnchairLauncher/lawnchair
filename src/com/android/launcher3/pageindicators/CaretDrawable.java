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
import android.graphics.drawable.Drawable;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public class CaretDrawable extends Drawable {
    public static final float PROGRESS_CARET_POINTING_UP = -1f;
    public static final float PROGRESS_CARET_POINTING_DOWN = 1f;
    public static final float PROGRESS_CARET_NEUTRAL = 0;

    private float mCaretProgress = PROGRESS_CARET_NEUTRAL;

    private Paint mShadowPaint = new Paint();
    private Paint mCaretPaint = new Paint();
    private Path mPath = new Path();
    private final int mCaretSizePx;
    private final boolean mUseShadow;

    public CaretDrawable(Context context) {
        final Resources res = context.getResources();

        final int strokeWidth = res.getDimensionPixelSize(R.dimen.all_apps_caret_stroke_width);
        final int shadowSpread = res.getDimensionPixelSize(R.dimen.all_apps_caret_shadow_spread);

        mCaretPaint.setColor(Themes.getAttrColor(context, R.attr.workspaceTextColor));
        mCaretPaint.setAntiAlias(true);
        mCaretPaint.setStrokeWidth(strokeWidth);
        mCaretPaint.setStyle(Paint.Style.STROKE);
        mCaretPaint.setStrokeCap(Paint.Cap.ROUND);
        mCaretPaint.setStrokeJoin(Paint.Join.ROUND);

        mShadowPaint.setColor(res.getColor(R.color.default_shadow_color_no_alpha));
        mShadowPaint.setAlpha(Themes.getAlpha(context, android.R.attr.spotShadowAlpha));
        mShadowPaint.setAntiAlias(true);
        mShadowPaint.setStrokeWidth(strokeWidth + (shadowSpread * 2));
        mShadowPaint.setStyle(Paint.Style.STROKE);
        mShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        mShadowPaint.setStrokeJoin(Paint.Join.ROUND);

        mUseShadow = !Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText);
        mCaretSizePx = res.getDimensionPixelSize(R.dimen.all_apps_caret_size);
    }

    @Override
    public int getIntrinsicHeight() {
        return mCaretSizePx;
    }

    @Override
    public int getIntrinsicWidth() {
        return mCaretSizePx;
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

        // When the bounds are square, this will result in a caret with a right angle
        final float verticalInset = (height / 4);
        final float caretHeight = (height - (verticalInset * 2));

        mPath.reset();
        mPath.moveTo(left, top + caretHeight * (1 - getNormalizedCaretProgress()));
        mPath.lineTo(left + (width / 2), top + caretHeight * getNormalizedCaretProgress());
        mPath.lineTo(left + width, top + caretHeight * (1 - getNormalizedCaretProgress()));
        if (mUseShadow) {
            canvas.drawPath(mPath, mShadowPaint);
        }
        canvas.drawPath(mPath, mCaretPaint);
    }

    /**
     * Sets the caret progress
     *
     * @param progress The progress ({@value #PROGRESS_CARET_POINTING_UP} for pointing up,
     * {@value #PROGRESS_CARET_POINTING_DOWN} for pointing down, {@value #PROGRESS_CARET_NEUTRAL}
     * for neutral)
     */
    public void setCaretProgress(float progress) {
        mCaretProgress = progress;
        invalidateSelf();
    }

    /**
     * Returns the caret progress
     *
     * @return The progress
     */
    public float getCaretProgress() {
        return mCaretProgress;
    }

    /**
     * Returns the caret progress normalized to [0..1]
     *
     * @return The normalized progress
     */
    public float getNormalizedCaretProgress() {
        return (mCaretProgress - PROGRESS_CARET_POINTING_UP) /
                (PROGRESS_CARET_POINTING_DOWN - PROGRESS_CARET_POINTING_UP);
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
