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

    public static final int OUTER_BLUR_RADIUS;

    private static final BlurMaskFilter sThickOuterBlurMaskFilter;
    private static final BlurMaskFilter sMediumOuterBlurMaskFilter;
    private static final BlurMaskFilter sThinOuterBlurMaskFilter;
    private static final BlurMaskFilter sThickInnerBlurMaskFilter;

    static {
        final float scale = LauncherApplication.getScreenDensity();

        OUTER_BLUR_RADIUS = (int) (scale * 6.0f);

        sThickOuterBlurMaskFilter = new BlurMaskFilter(OUTER_BLUR_RADIUS,
                BlurMaskFilter.Blur.OUTER);
        sMediumOuterBlurMaskFilter = new BlurMaskFilter(scale * 2.0f, BlurMaskFilter.Blur.OUTER);
        sThinOuterBlurMaskFilter = new BlurMaskFilter(scale * 1.0f, BlurMaskFilter.Blur.OUTER);
        sThickInnerBlurMaskFilter = new BlurMaskFilter(scale * 4.0f, BlurMaskFilter.Blur.NORMAL);
    }

    private static final MaskFilter sFineClipTable = TableMaskFilter.CreateClipTable(0, 20);
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
     * Draws a solid outline around a bitmap, erasing the original pixels.
     *
     * @param bitmap The bitmap to modify
     * @param canvas A canvas on the bitmap
     * @param color The color to draw the outline and glow in
     * @param removeOrig If true, punch out the original pixels to just leave the outline
     */
    void applyExpensiveOuterOutline(Bitmap bitmap, Canvas canvas, int color, boolean removeOrig) {
        Bitmap originalImage = null;
        if (removeOrig) {
            originalImage = bitmap.extractAlpha();
        }

        // Compute an outer blur on the original bitmap
        mBlurPaint.setMaskFilter(sMediumOuterBlurMaskFilter);
        Bitmap outline = bitmap.extractAlpha(mBlurPaint, mTempOffset);

        // Paint the blurred bitmap back into the canvas. Using the clip table causes any alpha
        // pixels above a certain threshold to be rounded up to be fully opaque. This gives the
        // effect of a thick outline, with a slight blur on the edge
        mHolographicPaint.setColor(color);
        mHolographicPaint.setMaskFilter(sFineClipTable);
        canvas.drawBitmap(outline, mTempOffset[0], mTempOffset[1], mHolographicPaint);
        outline.recycle();

        if (removeOrig) {
            // Finally, punch out the original pixels, leaving just the outline
            canvas.drawBitmap(originalImage, 0, 0, mErasePaint);
            originalImage.recycle();
        }
    }

    /**
     * Applies a more expensive and accurate outline to whatever is currently drawn in a specified
     * bitmap.
     */
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        // calculate the outer blur first
        mBlurPaint.setMaskFilter(sThickOuterBlurMaskFilter);
        int[] outerBlurOffset = new int[2];
        Bitmap thickOuterBlur = srcDst.extractAlpha(mBlurPaint, outerBlurOffset);
        mBlurPaint.setMaskFilter(sThinOuterBlurMaskFilter);
        int[] thinOuterBlurOffset = new int[2];
        Bitmap thinOuterBlur = srcDst.extractAlpha(mBlurPaint, thinOuterBlurOffset);

        // calculate the inner blur
        srcDstCanvas.drawColor(0xFF000000, PorterDuff.Mode.SRC_OUT);
        mBlurPaint.setMaskFilter(sThickInnerBlurMaskFilter);
        int[] thickInnerBlurOffset = new int[2];
        Bitmap thickInnerBlur = srcDst.extractAlpha(mBlurPaint, thickInnerBlurOffset);

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
