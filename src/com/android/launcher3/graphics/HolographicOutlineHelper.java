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

package com.android.launcher3.graphics;

import static com.android.launcher3.ItemInfoWithIcon.FLAG_ADAPTIVE_ICON;

import android.content.Context;
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

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.R;

/**
 * Utility class to generate shadow and outline effect, which are used for click feedback
 * and drag-n-drop respectively.
 */
public class HolographicOutlineHelper {

    /**
     * Bitmap used as shadow for Adaptive icons
     */
    public static final Bitmap ADAPTIVE_ICON_SHADOW_BITMAP =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);

    private static HolographicOutlineHelper sInstance;

    private final Canvas mCanvas = new Canvas();
    private final Paint mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mErasePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final float mShadowBitmapShift;
    private final BlurMaskFilter mShadowBlurMaskFilter;

    // We have 4 different icon sizes: homescreen, hotseat, folder & all-apps
    private final SparseArray<Bitmap> mBitmapCache = new SparseArray<>(4);

    private HolographicOutlineHelper(Context context) {
        mShadowBitmapShift = context.getResources().getDimension(R.dimen.blur_size_click_shadow);
        mShadowBlurMaskFilter = new BlurMaskFilter(mShadowBitmapShift, BlurMaskFilter.Blur.NORMAL);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    public static HolographicOutlineHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HolographicOutlineHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    public Bitmap createMediumDropShadow(BubbleTextView view) {
        if (view.getTag() instanceof ItemInfoWithIcon &&
                ((((ItemInfoWithIcon) view.getTag()).runtimeStatusFlags & FLAG_ADAPTIVE_ICON)
                        != 0)) {
            return ADAPTIVE_ICON_SHADOW_BITMAP;
        }
        Drawable drawable = view.getIcon();
        if (drawable == null) {
            return null;
        }

        float scaleX = view.getScaleX();
        float scaleY = view.getScaleY();
        Rect rect = drawable.getBounds();

        int bitmapWidth = (int) (rect.width() * scaleX);
        int bitmapHeight = (int) (rect.height() * scaleY);
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
        mCanvas.scale(scaleX, scaleY);
        mCanvas.translate(-rect.left, -rect.top);
        drawable.draw(mCanvas);
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
        if (bitmap != null && bitmap != ADAPTIVE_ICON_SHADOW_BITMAP) {
            mBitmapCache.put((bitmap.getWidth() << 16) | bitmap.getHeight(), bitmap);
        }
    }
}
