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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region.Op;

public class HolographicOutlineHelper {

    private static final Rect sTempRect = new Rect();

    private final Canvas mCanvas = new Canvas();
    private final Paint mDrawPaint = new Paint();
    private final Paint mBlurPaint = new Paint();
    private final Paint mErasePaint = new Paint();

    private final BlurMaskFilter mMediumOuterBlurMaskFilter;
    private final BlurMaskFilter mThinOuterBlurMaskFilter;
    private final BlurMaskFilter mMediumInnerBlurMaskFilter;

    private final BlurMaskFilter mShaowBlurMaskFilter;
    private final int mShadowOffset;

    /**
     * Padding used when creating shadow bitmap;
     */
    final int shadowBitmapPadding;

    static HolographicOutlineHelper INSTANCE;

    private HolographicOutlineHelper(Context context) {
        final float scale = LauncherAppState.getInstance().getScreenDensity();

        mMediumOuterBlurMaskFilter = new BlurMaskFilter(scale * 2.0f, BlurMaskFilter.Blur.OUTER);
        mThinOuterBlurMaskFilter = new BlurMaskFilter(scale * 1.0f, BlurMaskFilter.Blur.OUTER);
        mMediumInnerBlurMaskFilter = new BlurMaskFilter(scale * 2.0f, BlurMaskFilter.Blur.NORMAL);

        mShaowBlurMaskFilter = new BlurMaskFilter(scale * 4.0f, BlurMaskFilter.Blur.NORMAL);
        mShadowOffset = (int) (scale * 2.0f);
        shadowBitmapPadding = (int) (scale * 4.0f);

        mDrawPaint.setFilterBitmap(true);
        mDrawPaint.setAntiAlias(true);
        mBlurPaint.setFilterBitmap(true);
        mBlurPaint.setAntiAlias(true);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        mErasePaint.setAntiAlias(true);
    }

    public static HolographicOutlineHelper obtain(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new HolographicOutlineHelper(context);
        }
        return INSTANCE;
    }

    /**
     * Applies a more expensive and accurate outline to whatever is currently drawn in a specified
     * bitmap.
     */
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor) {
        applyExpensiveOutlineWithBlur(srcDst, srcDstCanvas, color, outlineColor, true);
    }
    void applyExpensiveOutlineWithBlur(Bitmap srcDst, Canvas srcDstCanvas, int color,
            int outlineColor, boolean clipAlpha) {

        // We start by removing most of the alpha channel so as to ignore shadows, and
        // other types of partial transparency when defining the shape of the object
        if (clipAlpha) {
            int[] srcBuffer = new int[srcDst.getWidth() * srcDst.getHeight()];
            srcDst.getPixels(srcBuffer,
                    0, srcDst.getWidth(), 0, 0, srcDst.getWidth(), srcDst.getHeight());
            for (int i = 0; i < srcBuffer.length; i++) {
                final int alpha = srcBuffer[i] >>> 24;
                if (alpha < 188) {
                    srcBuffer[i] = 0;
                }
            }
            srcDst.setPixels(srcBuffer,
                    0, srcDst.getWidth(), 0, 0, srcDst.getWidth(), srcDst.getHeight());
        }
        Bitmap glowShape = srcDst.extractAlpha();

        // calculate the outer blur first
        mBlurPaint.setMaskFilter(mMediumOuterBlurMaskFilter);
        int[] outerBlurOffset = new int[2];
        Bitmap thickOuterBlur = glowShape.extractAlpha(mBlurPaint, outerBlurOffset);

        mBlurPaint.setMaskFilter(mThinOuterBlurMaskFilter);
        int[] brightOutlineOffset = new int[2];
        Bitmap brightOutline = glowShape.extractAlpha(mBlurPaint, brightOutlineOffset);

        // calculate the inner blur
        srcDstCanvas.setBitmap(glowShape);
        srcDstCanvas.drawColor(0xFF000000, PorterDuff.Mode.SRC_OUT);
        mBlurPaint.setMaskFilter(mMediumInnerBlurMaskFilter);
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
        mDrawPaint.setColor(color);
        srcDstCanvas.drawBitmap(thickInnerBlur, thickInnerBlurOffset[0], thickInnerBlurOffset[1],
                mDrawPaint);
        srcDstCanvas.drawBitmap(thickOuterBlur, outerBlurOffset[0], outerBlurOffset[1],
                mDrawPaint);

        // draw the bright outline
        mDrawPaint.setColor(outlineColor);
        srcDstCanvas.drawBitmap(brightOutline, brightOutlineOffset[0], brightOutlineOffset[1],
                mDrawPaint);

        // cleanup
        srcDstCanvas.setBitmap(null);
        brightOutline.recycle();
        thickOuterBlur.recycle();
        thickInnerBlur.recycle();
        glowShape.recycle();
    }

    Bitmap createMediumDropShadow(BubbleTextView view) {
        final Bitmap result = Bitmap.createBitmap(
                view.getWidth() + shadowBitmapPadding + shadowBitmapPadding,
                view.getHeight() + shadowBitmapPadding + shadowBitmapPadding + mShadowOffset,
                Bitmap.Config.ARGB_8888);

        mCanvas.setBitmap(result);

        final Rect clipRect = sTempRect;
        view.getDrawingRect(sTempRect);
        // adjust the clip rect so that we don't include the text label
        clipRect.bottom = view.getExtendedPaddingTop() - (int) BubbleTextView.PADDING_V
                + view.getLayout().getLineTop(0);

        // Draw the View into the bitmap.
        // The translate of scrollX and scrollY is necessary when drawing TextViews, because
        // they set scrollX and scrollY to large values to achieve centered text
        mCanvas.save();
        mCanvas.scale(view.getScaleX(), view.getScaleY(),
                view.getWidth() / 2 + shadowBitmapPadding,
                view.getHeight() / 2 + shadowBitmapPadding);
        mCanvas.translate(-view.getScrollX() + shadowBitmapPadding,
                -view.getScrollY() + shadowBitmapPadding);
        mCanvas.clipRect(clipRect, Op.REPLACE);
        view.draw(mCanvas);
        mCanvas.restore();

        int[] blurOffst = new int[2];
        mBlurPaint.setMaskFilter(mShaowBlurMaskFilter);
        Bitmap blurBitmap = result.extractAlpha(mBlurPaint, blurOffst);

        mCanvas.save();
        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        mCanvas.translate(blurOffst[0], blurOffst[1]);

        mDrawPaint.setColor(Color.BLACK);
        mDrawPaint.setAlpha(30);
        mCanvas.drawBitmap(blurBitmap, 0, 0, mDrawPaint);

        mDrawPaint.setAlpha(60);
        mCanvas.drawBitmap(blurBitmap, 0, mShadowOffset, mDrawPaint);
        mCanvas.restore();

        mCanvas.setBitmap(null);
        blurBitmap.recycle();

        return result;
    }
}
