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
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public class HolographicOutlineHelper {
    private final Paint mHolographicPaint = new Paint();
    private final Paint mExpensiveBlurPaint = new Paint();
    private final Paint mErasePaint = new Paint();

    private static final BlurMaskFilter mThickOuterBlurMaskFilter = new BlurMaskFilter(6.0f,
            BlurMaskFilter.Blur.OUTER);
    private static final BlurMaskFilter mThinOuterBlurMaskFilter = new BlurMaskFilter(1.0f,
            BlurMaskFilter.Blur.OUTER);
    private static final BlurMaskFilter mThickInnerBlurMaskFilter = new BlurMaskFilter(4.0f,
            BlurMaskFilter.Blur.NORMAL);

    public static float DEFAULT_STROKE_WIDTH = 6.0f;

    HolographicOutlineHelper() {
        mHolographicPaint.setFilterBitmap(true);
        mHolographicPaint.setAntiAlias(true);
        mExpensiveBlurPaint.setFilterBitmap(true);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        mErasePaint.setAntiAlias(true);
    }

    /**
     * Returns the interpolated holographic highlight alpha for the effect we want when scrolling
     * pages.
     */
    public float highlightAlphaInterpolator(float r) {
        float maxAlpha = 0.8f;
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
     * Applies a more expensive and accurate outline to whatever is currently drawn in a specified
     * bitmap.
     */
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        // calculate the outer blur first
        mExpensiveBlurPaint.setMaskFilter(mThickOuterBlurMaskFilter);
        int[] outerBlurOffset = new int[2];
        Bitmap thickOuterBlur = srcDst.extractAlpha(mExpensiveBlurPaint, outerBlurOffset);
        mExpensiveBlurPaint.setMaskFilter(mThinOuterBlurMaskFilter);
        int[] thinOuterBlurOffset = new int[2];
        Bitmap thinOuterBlur = srcDst.extractAlpha(mExpensiveBlurPaint, thinOuterBlurOffset);

        // calculate the inner blur
        srcDstCanvas.drawColor(0xFF000000, PorterDuff.Mode.SRC_OUT);
        mExpensiveBlurPaint.setMaskFilter(mThickInnerBlurMaskFilter);
        int[] thickInnerBlurOffset = new int[2];
        Bitmap thickInnerBlur = srcDst.extractAlpha(mExpensiveBlurPaint, thickInnerBlurOffset);

        // mask out the inner blur
        srcDstCanvas.setBitmap(thickInnerBlur);
        srcDstCanvas.drawBitmap(srcDst, -thickInnerBlurOffset[0],
                -thickInnerBlurOffset[1], mErasePaint);
        srcDstCanvas.drawRect(0, 0, -thickInnerBlurOffset[0], thickInnerBlur.getHeight(),
                mErasePaint);
        srcDstCanvas.drawRect(0, 0, thickInnerBlur.getWidth(), -thickInnerBlurOffset[1],
                mErasePaint);

        // draw the inner and outer blur
        srcDstCanvas.setBitmap(srcDst);
        srcDstCanvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        mHolographicPaint.setColor(color);
        srcDstCanvas.drawBitmap(thickInnerBlur, thickInnerBlurOffset[0], thickInnerBlurOffset[1],
                mHolographicPaint);
        srcDstCanvas.drawBitmap(thickOuterBlur, outerBlurOffset[0], outerBlurOffset[1],
                mHolographicPaint);

        // draw the bright outline
        mHolographicPaint.setColor(outlineColor);
        srcDstCanvas.drawBitmap(thinOuterBlur, thinOuterBlurOffset[0], thinOuterBlurOffset[1],
                mHolographicPaint);

        // cleanup
        thinOuterBlur.recycle();
        thickOuterBlur.recycle();
        thickInnerBlur.recycle();
    }
}