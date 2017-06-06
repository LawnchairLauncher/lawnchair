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

package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.graphics.ColorUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.Preconditions;

/**
 * Utility class to add shadows to bitmaps.
 */
public class ShadowGenerator {

    // Percent of actual icon size
    private static final float HALF_DISTANCE = 0.5f;
    public static final float BLUR_FACTOR = 0.5f/48;

    // Percent of actual icon size
    private static final float KEY_SHADOW_DISTANCE = 1f/48;
    private static final int KEY_SHADOW_ALPHA = 61;

    private static final int AMBIENT_SHADOW_ALPHA = 30;

    private static final Object LOCK = new Object();
    // Singleton object guarded by {@link #LOCK}
    private static ShadowGenerator sShadowGenerator;

    private final int mIconSize;

    private final Canvas mCanvas;
    private final Paint mBlurPaint;
    private final Paint mDrawPaint;

    private ShadowGenerator(Context context) {
        mIconSize = LauncherAppState.getIDP(context).iconBitmapSize;
        mCanvas = new Canvas();
        mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mBlurPaint.setMaskFilter(new BlurMaskFilter(mIconSize * BLUR_FACTOR, Blur.NORMAL));
        mDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    public synchronized Bitmap recreateIcon(Bitmap icon) {
        int[] offset = new int[2];
        Bitmap shadow = icon.extractAlpha(mBlurPaint, offset);
        Bitmap result = Bitmap.createBitmap(mIconSize, mIconSize, Config.ARGB_8888);
        mCanvas.setBitmap(result);

        // Draw ambient shadow
        mDrawPaint.setAlpha(AMBIENT_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1], mDrawPaint);

        // Draw key shadow
        mDrawPaint.setAlpha(KEY_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1] + KEY_SHADOW_DISTANCE * mIconSize, mDrawPaint);

        // Draw the icon
        mDrawPaint.setAlpha(255);
        mCanvas.drawBitmap(icon, 0, 0, mDrawPaint);

        mCanvas.setBitmap(null);
        return result;
    }

    public static Bitmap createPillWithShadow(int rectColor, int width, int height) {
        float shadowRadius = height * 1f / 32;
        float shadowYOffset = height * 1f / 16;
        return createPillWithShadow(rectColor, width, height, shadowRadius, shadowYOffset,
                new RectF());
    }

    public static Bitmap createPillWithShadow(int rectColor, int width, int height,
            float shadowRadius, float shadowYOffset, RectF outRect) {
        int radius = height / 2;

        int centerX = Math.round(width / 2 + shadowRadius);
        int centerY = Math.round(radius + shadowRadius + shadowYOffset);
        int center = Math.max(centerX, centerY);
        int size = center * 2;
        Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);

        outRect.set(0, 0, width, height);
        outRect.offsetTo(center - width / 2, center - height / 2);

        drawShadow(new Canvas(result), outRect, rectColor, shadowRadius, shadowYOffset, radius);
        return result;
    }

    public static void drawShadow(Canvas c, RectF bounds, int color,
            float shadowBlur, float keyShadowDistance, float radius) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(color);

        // Key shadow
        p.setShadowLayer(shadowBlur, 0, keyShadowDistance,
                ColorUtils.setAlphaComponent(Color.BLACK, KEY_SHADOW_ALPHA));
        c.drawRoundRect(bounds, radius, radius, p);

        // Ambient shadow
        p.setShadowLayer(shadowBlur, 0, 0,
                ColorUtils.setAlphaComponent(Color.BLACK, AMBIENT_SHADOW_ALPHA));
        c.drawRoundRect(bounds, radius, radius, p);
    }

    public static ShadowGenerator getInstance(Context context) {
        Preconditions.assertNonUiThread();
        synchronized (LOCK) {
            if (sShadowGenerator == null) {
                sShadowGenerator = new ShadowGenerator(context);
            }
        }
        return sShadowGenerator;
    }

    /**
     * Returns the minimum amount by which an icon with {@param bounds} should be scaled
     * so that the shadows do not get clipped.
     */
    public static float getScaleForBounds(RectF bounds) {
        float scale = 1;

        // For top, left & right, we need same space.
        float minSide = Math.min(Math.min(bounds.left, bounds.right), bounds.top);
        if (minSide < BLUR_FACTOR) {
            scale = (HALF_DISTANCE - BLUR_FACTOR) / (HALF_DISTANCE - minSide);
        }

        float bottomSpace = BLUR_FACTOR + KEY_SHADOW_DISTANCE;
        if (bounds.bottom < bottomSpace) {
            scale = Math.min(scale, (HALF_DISTANCE - bottomSpace) / (HALF_DISTANCE - bounds.bottom));
        }
        return scale;
    }
}
