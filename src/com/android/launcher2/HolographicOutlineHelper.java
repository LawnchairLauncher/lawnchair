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
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.TableMaskFilter;

public class HolographicOutlineHelper {
    private final Paint mHolographicPaint = new Paint();
    private final Paint mBlurPaint = new Paint();
    private final Paint mErasePaint = new Paint();
    private final Paint mAlphaClipPaint = new Paint();

    public static final int MAX_OUTER_BLUR_RADIUS;
    public static final int MIN_OUTER_BLUR_RADIUS;

    private static final BlurMaskFilter sExtraThickOuterBlurMaskFilter;
    private static final BlurMaskFilter sThickOuterBlurMaskFilter;
    private static final BlurMaskFilter sMediumOuterBlurMaskFilter;
    private static final BlurMaskFilter sThinOuterBlurMaskFilter;
    private static final BlurMaskFilter sThickInnerBlurMaskFilter;
    private static final BlurMaskFilter sExtraThickInnerBlurMaskFilter;
    private static final BlurMaskFilter sMediumInnerBlurMaskFilter;

    private static final int THICK = 0;
    private static final int MEDIUM = 1;
    private static final int EXTRA_THICK = 2;

    static {
        final float scale = LauncherApplication.getScreenDensity();

        MIN_OUTER_BLUR_RADIUS = (int) (scale * 1.0f);
        MAX_OUTER_BLUR_RADIUS = (int) (scale * 12.0f);

        sExtraThickOuterBlurMaskFilter = new BlurMaskFilter(scale * 12.0f, BlurMaskFilter.Blur.OUTER);
        sThickOuterBlurMaskFilter = new BlurMaskFilter(scale * 6.0f, BlurMaskFilter.Blur.OUTER);
        sMediumOuterBlurMaskFilter = new BlurMaskFilter(scale * 2.0f, BlurMaskFilter.Blur.OUTER);
        sThinOuterBlurMaskFilter = new BlurMaskFilter(scale * 1.0f, BlurMaskFilter.Blur.OUTER);
        sExtraThickInnerBlurMaskFilter = new BlurMaskFilter(scale * 6.0f, BlurMaskFilter.Blur.NORMAL);
        sThickInnerBlurMaskFilter = new BlurMaskFilter(scale * 4.0f, BlurMaskFilter.Blur.NORMAL);
        sMediumInnerBlurMaskFilter = new BlurMaskFilter(scale * 2.0f, BlurMaskFilter.Blur.NORMAL);
    }

    private static final MaskFilter sCoarseClipTable = TableMaskFilter.CreateClipTable(0, 200);

    private int[] mTempOffset = new int[2];

    HolographicOutlineHelper() {
        mHolographicPaint.setFilterBitmap(true);
        mHolographicPaint.setAntiAlias(true);
        mBlurPaint.setFilterBitmap(true);
        mBlurPaint.setAntiAlias(true);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        mErasePaint.setAntiAlias(true);
        MaskFilter alphaClipTable = TableMaskFilter.CreateClipTable(180, 255);
        mAlphaClipPaint.setMaskFilter(alphaClipTable);
    }

    /**
     * Returns the interpolated holographic highlight alpha for the effect we want when scrolling
     * pages.
     */
    public static float highlightAlphaInterpolator(float r) {
        float maxAlpha = 0.6f;
        return (float) Math.pow(maxAlpha * (1.0f - r), 1.5f);
    }

    /**
     * Returns the interpolated view alpha for the effect we want when scrolling pages.
     */
    public static float viewAlphaInterpolator(float r) {
        final float pivot = 0.95f;
        if (r < pivot) {
            return (float) Math.pow(r / pivot, 1.5f);
        } else {
            return 1.0f;
        }
    }

    /**
     * Apply an outer blur to the given bitmap.
     * You should use OUTER_BLUR_RADIUS to ensure that the bitmap is big enough to draw
     * the blur without clipping.
     */
    void applyOuterBlur(Bitmap bitmap, Canvas canvas, int color) {
        mBlurPaint.setMaskFilter(sThickOuterBlurMaskFilter);
        Bitmap glow = bitmap.extractAlpha(mBlurPaint, mTempOffset);

        // Use the clip table to make the glow heavier closer to the outline
        mHolographicPaint.setMaskFilter(sCoarseClipTable);
        mHolographicPaint.setAlpha(150);
        mHolographicPaint.setColor(color);

        canvas.drawBitmap(glow, mTempOffset[0], mTempOffset[1], mHolographicPaint);
        glow.recycle();
    }

    /**
     * Applies a more expensive and accurate outline to whatever is currently drawn in a specified
     * bitmap.
     */
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor, int thickness) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, mAlphaClipPaint,
                thickness);
    }
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor, Paint alphaClipPaint, int thickness) {

        // We start by removing most of the alpha channel so as to ignore shadows, and
        // other types of partial transparency when defining the shape of the object
        if (alphaClipPaint == null) {
            alphaClipPaint = mAlphaClipPaint;
        }
        Bitmap glowShape = srcDst.extractAlpha(alphaClipPaint, mTempOffset);

        // calculate the outer blur first
        BlurMaskFilter outerBlurMaskFilter;
        switch (thickness) {
            case EXTRA_THICK:
                outerBlurMaskFilter = sExtraThickOuterBlurMaskFilter;
                break;
            case THICK:
                outerBlurMaskFilter = sThickOuterBlurMaskFilter;
                break;
            case MEDIUM:
                outerBlurMaskFilter = sMediumOuterBlurMaskFilter;
                break;
            default:
                throw new RuntimeException("Invalid blur thickness");
        }
        mBlurPaint.setMaskFilter(outerBlurMaskFilter);
        int[] outerBlurOffset = new int[2];
        Bitmap thickOuterBlur = glowShape.extractAlpha(mBlurPaint, outerBlurOffset);
        if (thickness == EXTRA_THICK) {
            mBlurPaint.setMaskFilter(sMediumOuterBlurMaskFilter);
        } else {
            mBlurPaint.setMaskFilter(sThinOuterBlurMaskFilter);
        }

        int[] brightOutlineOffset = new int[2];
        Bitmap brightOutline = glowShape.extractAlpha(mBlurPaint, brightOutlineOffset);

        // calculate the inner blur
        srcDstCanvas.setBitmap(glowShape);
        srcDstCanvas.drawColor(0xFF000000, PorterDuff.Mode.SRC_OUT);
        BlurMaskFilter innerBlurMaskFilter;
        switch (thickness) {
            case EXTRA_THICK:
                innerBlurMaskFilter = sExtraThickInnerBlurMaskFilter;
                break;
            case THICK:
                innerBlurMaskFilter = sThickInnerBlurMaskFilter;
                break;
            case MEDIUM:
                innerBlurMaskFilter = sMediumInnerBlurMaskFilter;
                break;
            default:
                throw new RuntimeException("Invalid blur thickness");
        }
        mBlurPaint.setMaskFilter(innerBlurMaskFilter);
        int[] thickInnerBlurOffset = new int[2];
        Bitmap thickInnerBlur = glowShape.extractAlpha(mBlurPaint, thickInnerBlurOffset);

        // mask out the inner blur
        srcDstCanvas.setBitmap(thickInnerBlur);
        srcDstCanvas.drawBitmap(glowShape, -thickInnerBlurOffset[0],
                -thickInnerBlurOffset[1], mErasePaint);
        srcDstCanvas.drawRect(0, 0, -thickInnerBlurOffset[0], thickInnerBlur.getHeight(),
                mErasePaint);
        srcDstCanvas.drawRect(0, 0, thickInnerBlur.getWidth(), -thickInnerBlurOffset[1],
                mErasePaint);

        // draw the inner and outer blur
        srcDstCanvas.setBitmap(srcDst);
        srcDstCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mHolographicPaint.setColor(color);
        srcDstCanvas.drawBitmap(thickInnerBlur, thickInnerBlurOffset[0], thickInnerBlurOffset[1],
                mHolographicPaint);
        srcDstCanvas.drawBitmap(thickOuterBlur, outerBlurOffset[0], outerBlurOffset[1],
                mHolographicPaint);

        // draw the bright outline
        mHolographicPaint.setColor(outlineColor);
        srcDstCanvas.drawBitmap(brightOutline, brightOutlineOffset[0], brightOutlineOffset[1],
                mHolographicPaint);

        // cleanup
        srcDstCanvas.setBitmap(null);
        brightOutline.recycle();
        thickOuterBlur.recycle();
        thickInnerBlur.recycle();
        glowShape.recycle();
    }

    void applyExtraThickExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, EXTRA_THICK);
    }

    void applyThickExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, THICK);
    }

    void applyMediumExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor, Paint alphaClipPaint) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, alphaClipPaint,
                MEDIUM);
    }

    void applyMediumExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, MEDIUM);
    }

}
