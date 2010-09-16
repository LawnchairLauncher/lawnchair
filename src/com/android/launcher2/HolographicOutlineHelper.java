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

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public class HolographicOutlineHelper {
    private float mDensity;
    private final Paint mHolographicPaint = new Paint();
    private final Paint mBlurPaint = new Paint();
    private final Paint mErasePaint = new Paint();
    private static final Matrix mIdentity = new Matrix();;
    private static final float BLUR_FACTOR = 3.5f;

    public static final float DEFAULT_STROKE_WIDTH = 6.0f;
    public static final int HOLOGRAPHIC_BLUE = 0xFF6699FF;
    public static final int HOLOGRAPHIC_GREEN = 0xFF51E633;

    HolographicOutlineHelper(float density) {
        mDensity = density;
        mHolographicPaint.setColor(HOLOGRAPHIC_BLUE);
        mHolographicPaint.setFilterBitmap(true);
        mHolographicPaint.setAntiAlias(true);
        mBlurPaint.setMaskFilter(new BlurMaskFilter(BLUR_FACTOR * density,
                BlurMaskFilter.Blur.OUTER));
        mBlurPaint.setFilterBitmap(true);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        mErasePaint.setAntiAlias(true);
    }

    private float cubic(float r) {
        return (float) (Math.pow(r - 1, 3) + 1);
    }

    /**
     * Returns the interpolated holographic highlight alpha for the effect we want when scrolling
     * pages.
     */
    public float highlightAlphaInterpolator(float r) {
        float maxAlpha = 0.6f;
        return (float) Math.pow(maxAlpha * (1.0f - r), 1.5f);
    }

    /**
     * Returns the interpolated view alpha for the effect we want when scrolling pages.
     */
    public float viewAlphaInterpolator(float r) {
        final float pivot = 0.95f;
        if (r < pivot) {
            return (float) Math.pow(r / pivot, 1.5f);
        } else {
            return 1.0f;
        }
    }

    /**
     * Sets the color of the holographic paint to be used when applying the outline/blur.
     */
    void setColor(int color) {
        mHolographicPaint.setColor(color);
    }

    /**
     * Applies an outline to whatever is currently drawn in the specified bitmap.
     */
    void applyOutline(Bitmap srcDst, Canvas srcDstCanvas, float strokeWidth, PointF offset) {
        strokeWidth *= mDensity;
        Bitmap mask = srcDst.extractAlpha();
        Matrix m = new Matrix();
        final int width = srcDst.getWidth();
        final int height = srcDst.getHeight();
        float xScale = strokeWidth * 2.0f / width;
        float yScale = strokeWidth * 2.0f / height;
        m.preScale(1 + xScale, 1 + yScale, (width / 2.0f) + offset.x,
                (height / 2.0f) + offset.y);

        srcDstCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        srcDstCanvas.drawBitmap(mask, m, mHolographicPaint);
        srcDstCanvas.drawBitmap(mask, mIdentity, mErasePaint);
        mask.recycle();
    }

    /**
     * Applies an blur to whatever is currently drawn in the specified bitmap.
     */
    void applyBlur(Bitmap srcDst, Canvas srcDstCanvas) {
        int[] xy = new int[2];
        Bitmap mask = srcDst.extractAlpha(mBlurPaint, xy);
        srcDstCanvas.drawBitmap(mask, xy[0], xy[1], mHolographicPaint);
        mask.recycle();
    }
}