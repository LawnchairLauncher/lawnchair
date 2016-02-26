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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

/**
 * Utility class to generate shadow and outline effect, which are used for click feedback
 * and drag-n-drop respectively.
 */
public class HolographicOutlineHelper {

    private static HolographicOutlineHelper sInstance;

    private final Canvas mCanvas = new Canvas();
    private final Paint mDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mErasePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final BlurMaskFilter mMediumOuterBlurMaskFilter;
    private final BlurMaskFilter mThinOuterBlurMaskFilter;
    private final BlurMaskFilter mMediumInnerBlurMaskFilter;

    private final float mShadowBitmapShift;
    private final BlurMaskFilter mShadowBlurMaskFilter;

    // We have 4 different icon sizes: homescreen, hotseat, folder & all-apps
    private final SparseArray<Bitmap> mBitmapCache = new SparseArray<>(4);

    private HolographicOutlineHelper(Context context) {
        Resources res = context.getResources();

        float mediumBlur = res.getDimension(R.dimen.blur_size_medium_outline);
        mMediumOuterBlurMaskFilter = new BlurMaskFilter(mediumBlur, BlurMaskFilter.Blur.OUTER);
        mMediumInnerBlurMaskFilter = new BlurMaskFilter(mediumBlur, BlurMaskFilter.Blur.NORMAL);

        mThinOuterBlurMaskFilter = new BlurMaskFilter(
                res.getDimension(R.dimen.blur_size_thin_outline), BlurMaskFilter.Blur.OUTER);

        mShadowBitmapShift = res.getDimension(R.dimen.blur_size_click_shadow);
        mShadowBlurMaskFilter = new BlurMaskFilter(mShadowBitmapShift, BlurMaskFilter.Blur.NORMAL);

        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    public static HolographicOutlineHelper obtain(Context context) {
        if (sInstance == null) {
            sInstance = new HolographicOutlineHelper(context);
        }
        return sInstance;
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
        Drawable icon = view.getIcon();
        if (icon == null) {
            return null;
        }
        Rect rect = icon.getBounds();

        int bitmapWidth = (int) (rect.width() * view.getScaleX());
        int bitmapHeight = (int) (rect.height() * view.getScaleY());
        if (bitmapHeight <= 0 || bitmapWidth <= 0) {
            return null;
        }

        int key = (bitmapWidth << 16) | bitmapHeight;
        Bitmap cache = mBitmapCache.get(key);
        if (cache == null) {
            cache = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ALPHA_8);
            mCanvas.setBitmap(cache);
            mBitmapCache.put(key, cache);
        } else {
            mCanvas.setBitmap(cache);
            mCanvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
        }

        int saveCount = mCanvas.save();
        mCanvas.scale(view.getScaleX(), view.getScaleY());
        mCanvas.translate(-rect.left, -rect.top);
        icon.draw(mCanvas);
        mCanvas.restoreToCount(saveCount);
        mCanvas.setBitmap(null);

        mBlurPaint.setMaskFilter(mShadowBlurMaskFilter);

        int extraSize = (int) (2 * mShadowBitmapShift);

        int resultWidth = bitmapWidth + extraSize;
        int resultHeight = bitmapHeight + extraSize;
        key = (resultWidth << 16) | resultHeight;
        Bitmap result = mBitmapCache.get(key);
        if (result == null) {
            result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ALPHA_8);
            mCanvas.setBitmap(result);
        } else {
            // Use put instead of delete, to avoid unnecessary shrinking of cache array
            mBitmapCache.put(key, null);
            mCanvas.setBitmap(result);
            mCanvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
        }
        mCanvas.drawBitmap(cache, mShadowBitmapShift, mShadowBitmapShift, mBlurPaint);
        mCanvas.setBitmap(null);
        return result;
    }

    public void recycleShadowBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            mBitmapCache.put((bitmap.getWidth() << 16) | bitmap.getHeight(), bitmap);
        }
    }
}
