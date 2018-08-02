/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Utility class which draws a bitmap by dissecting it into 3 segments and stretching
 * the middle segment.
 */
public class NinePatchDrawHelper {

    // The extra width used for the bitmap. This portion of the bitmap is stretched to match the
    // width of the draw region. Randomly chosen, any value > 4 will be sufficient.
    public static final int EXTENSION_PX = 20;

    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    // Enable filtering to always get a nice edge. This avoids jagged line, when bitmap is
    // translated by half pixel.
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /**
     * Draws the bitmap split into three parts horizontally, with the middle part having width
     * as {@link #EXTENSION_PX} in the center of the bitmap.
     */
    public void draw(Bitmap bitmap, Canvas canvas, float left, float top, float right) {
        int height = bitmap.getHeight();

        mSrc.top = 0;
        mSrc.bottom = height;
        mDst.top = top;
        mDst.bottom = top + height;
        draw3Patch(bitmap, canvas, left, right);
    }


    /**
     * Draws the bitmap split horizontally into 3 parts (same as {@link #draw}) and split
     * vertically into two parts, bottom part of size {@link #EXTENSION_PX} / 2 which is
     * stretched vertically.
     */
    public void drawVerticallyStretched(Bitmap bitmap, Canvas canvas, float left, float top,
            float right, float bottom) {
        draw(bitmap, canvas, left, top, right);

        // Draw bottom stretched region.
        int height = bitmap.getHeight();
        mSrc.top = height - EXTENSION_PX / 4;
        mSrc.bottom = height;
        mDst.top = top + height;
        mDst.bottom = bottom;
        draw3Patch(bitmap, canvas, left, right);
    }



    private void draw3Patch(Bitmap bitmap, Canvas canvas, float left, float right) {
        int width = bitmap.getWidth();
        int halfWidth = width / 2;

        // Draw left edge
        drawRegion(bitmap, canvas, 0, halfWidth, left, left + halfWidth);

        // Draw right edge
        drawRegion(bitmap, canvas, halfWidth, width, right - halfWidth, right);

        // Draw middle stretched region
        int halfExt = EXTENSION_PX / 4;
        drawRegion(bitmap, canvas, halfWidth - halfExt, halfWidth + halfExt,
                left + halfWidth, right - halfWidth);
    }

    private void drawRegion(Bitmap bitmap, Canvas c,
            int srcLeft, int srcRight, float dstLeft, float dstRight) {
        mSrc.left = srcLeft;
        mSrc.right = srcRight;

        mDst.left = dstLeft;
        mDst.right = dstRight;
        c.drawBitmap(bitmap, mSrc, mDst, paint);
    }
}
