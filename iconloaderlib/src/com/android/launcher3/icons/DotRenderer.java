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

package com.android.launcher3.icons;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.ViewDebug;

/**
 * Used to draw a notification dot on top of an icon.
 */
public class DotRenderer {

    private static final String TAG = "DotRenderer";

    // The dot size is defined as a percentage of the app icon size.
    private static final float SIZE_PERCENTAGE = 0.38f;

    // Extra scale down of the dot
    private static final float DOT_SCALE = 0.6f;

    // Offset the dot slightly away from the icon if there's space.
    private static final float OFFSET_PERCENTAGE = 0.02f;

    private final float mDotCenterOffset;
    private final int mOffset;
    private final float mCircleRadius;
    private final Paint mCirclePaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

    private final Bitmap mBackgroundWithShadow;
    private final float mBitmapOffset;

    public DotRenderer(int iconSizePx) {
        mDotCenterOffset = SIZE_PERCENTAGE * iconSizePx;
        mOffset = (int) (OFFSET_PERCENTAGE * iconSizePx);

        int size = (int) (DOT_SCALE * mDotCenterOffset);
        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        builder.ambientShadowAlpha = 88;
        mBackgroundWithShadow = builder.setupBlurForSize(size).createPill(size, size);
        mCircleRadius = builder.radius;

        mBitmapOffset = -mBackgroundWithShadow.getHeight() * 0.5f; // Same as width.
    }

    /**
     * Draw a circle on top of the canvas according to the given params.
     */
    public void draw(Canvas canvas, DrawParams params) {
        if (params == null) {
            Log.e(TAG, "Invalid null argument(s) passed in call to draw.");
            return;
        }
        canvas.save();
        // We draw the dot relative to its center.
        float dotCenterX = params.leftAlign
                ? params.iconBounds.left + mDotCenterOffset / 2
                : params.iconBounds.right - mDotCenterOffset / 2;
        float dotCenterY = params.iconBounds.top + mDotCenterOffset / 2;

        int offsetX = Math.min(mOffset, params.spaceForOffset.x);
        int offsetY = Math.min(mOffset, params.spaceForOffset.y);
        canvas.translate(dotCenterX + offsetX, dotCenterY - offsetY);
        canvas.scale(params.scale, params.scale);

        mCirclePaint.setColor(Color.BLACK);
        canvas.drawBitmap(mBackgroundWithShadow, mBitmapOffset, mBitmapOffset, mCirclePaint);
        mCirclePaint.setColor(params.color);
        canvas.drawCircle(0, 0, mCircleRadius, mCirclePaint);
        canvas.restore();
    }

    public static class DrawParams {
        /** The color (possibly based on the icon) to use for the dot. */
        @ViewDebug.ExportedProperty(category = "notification dot", formatToHexString = true)
        public int color;
        /** The bounds of the icon that the dot is drawn on top of. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public Rect iconBounds = new Rect();
        /** The progress of the animation, from 0 to 1. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public float scale;
        /** Overrides internally calculated offset if specified value is smaller. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public Point spaceForOffset = new Point();
        /** Whether the dot should align to the top left of the icon rather than the top right. */
        @ViewDebug.ExportedProperty(category = "notification dot")
        public boolean leftAlign;
    }
}
