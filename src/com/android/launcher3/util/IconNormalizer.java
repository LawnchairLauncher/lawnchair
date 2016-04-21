/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import com.android.launcher3.LauncherAppState;

import java.nio.ByteBuffer;

public class IconNormalizer {

    // Ratio of icon visible area to full icon size for a square shaped icon
    private static final float MAX_SQUARE_AREA_FACTOR = 359.0f / 576;
    // Ratio of icon visible area to full icon size for a circular shaped icon
    private static final float MAX_CIRCLE_AREA_FACTOR = 380.0f / 576;

    private static final float CIRCLE_AREA_BY_RECT = (float) Math.PI / 4;

    // Slope used to calculate icon visible area to full icon size for any generic shaped icon.
    private static final float LINEAR_SCALE_SLOPE =
            (MAX_CIRCLE_AREA_FACTOR - MAX_SQUARE_AREA_FACTOR) / (1 - CIRCLE_AREA_BY_RECT);

    private static final int MIN_VISIBLE_ALPHA = 40;

    private static final Object LOCK = new Object();
    private static IconNormalizer sIconNormalizer;

    private final int mMaxSize;
    private final Bitmap mBitmap;
    private final Canvas mCanvas;
    private final byte[] mPixels;

    // for each y, stores the position of the leftmost x and the rightmost x
    private final float[] mLeftBorder;
    private final float[] mRightBorder;

    private IconNormalizer() {
        // Use twice the icon size as maximum size to avoid scaling down twice.
        mMaxSize = LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize * 2;
        mBitmap = Bitmap.createBitmap(mMaxSize, mMaxSize, Bitmap.Config.ALPHA_8);
        mCanvas = new Canvas(mBitmap);
        mPixels = new byte[mMaxSize * mMaxSize];

        mLeftBorder = new float[mMaxSize];
        mRightBorder = new float[mMaxSize];
    }

    /**
     * Returns the amount by which the {@param d} should be scaled (in both dimensions) so that it
     * matches the design guidelines for a launcher icon.
     *
     * We first calculate the convex hull of the visible portion of the icon.
     * This hull then compared with the bounding rectangle of the hull to find how closely it
     * resembles a circle and a square, by comparing the ratio of the areas. Note that this is not an
     * ideal solution but it gives satisfactory result without affecting the performance.
     *
     * This closeness is used to determine the ratio of hull area to the full icon size.
     * Refer {@link #MAX_CIRCLE_AREA_FACTOR} and {@link #MAX_SQUARE_AREA_FACTOR}
     */
    public synchronized float getScale(Drawable d) {
        int width = d.getIntrinsicWidth();
        int height = d.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = width <= 0 || width > mMaxSize ? mMaxSize : width;
            height = height <= 0 || height > mMaxSize ? mMaxSize : height;
        } else if (width > mMaxSize || height > mMaxSize) {
            int max = Math.max(width, height);
            width = mMaxSize * width / max;
            height = mMaxSize * height / max;
        }

        mBitmap.eraseColor(Color.TRANSPARENT);
        d.setBounds(0, 0, width, height);
        d.draw(mCanvas);

        ByteBuffer buffer = ByteBuffer.wrap(mPixels);
        buffer.rewind();
        mBitmap.copyPixelsToBuffer(buffer);

        // Overall bounds of the visible icon.
        int topY = -1;
        int bottomY = -1;
        int leftX = mMaxSize + 1;
        int rightX = -1;

        // Create border by going through all pixels one row at a time and for each row find
        // the first and the last non-transparent pixel. Set those values to mLeftBorder and
        // mRightBorder and use -1 if there are no visible pixel in the row.

        // buffer position
        int index = 0;
        // buffer shift after every row, width of buffer = mMaxSize
        int rowSizeDiff = mMaxSize - width;
        // first and last position for any row.
        int firstX, lastX;

        for (int y = 0; y < height; y++) {
            firstX = lastX = -1;
            for (int x = 0; x < width; x++) {
                if ((mPixels[index] & 0xFF) > MIN_VISIBLE_ALPHA) {
                    if (firstX == -1) {
                        firstX = x;
                    }
                    lastX = x;
                }
                index++;
            }
            index += rowSizeDiff;

            mLeftBorder[y] = firstX;
            mRightBorder[y] = lastX;

            // If there is at least one visible pixel, update the overall bounds.
            if (firstX != -1) {
                bottomY = y;
                if (topY == -1) {
                    topY = y;
                }

                leftX = Math.min(leftX, firstX);
                rightX = Math.max(rightX, lastX);
            }
        }

        if (topY == -1 || rightX == -1) {
            // No valid pixels found. Do not scale.
            return 1;
        }

        convertToConvexArray(mLeftBorder, 1, topY, bottomY);
        convertToConvexArray(mRightBorder, -1, topY, bottomY);

        // Area of the convex hull
        float area = 0;
        for (int y = 0; y < height; y++) {
            if (mLeftBorder[y] <= -1) {
                continue;
            }
            area += mRightBorder[y] - mLeftBorder[y] + 1;
        }

        // Area of the rectangle required to fit the convex hull
        float rectArea = (bottomY + 1 - topY) * (rightX + 1 - leftX);
        float hullByRect = area / rectArea;

        float scaleRequired;
        if (hullByRect < CIRCLE_AREA_BY_RECT) {
            scaleRequired = MAX_CIRCLE_AREA_FACTOR;
        } else {
            scaleRequired = MAX_SQUARE_AREA_FACTOR + LINEAR_SCALE_SLOPE * (1  - hullByRect);
        }

        float areaScale = area / (width * height);
        // Use sqrt of the final ratio as the images is scaled across both width and height.
        float scale = areaScale > scaleRequired ? (float) Math.sqrt(scaleRequired / areaScale) : 1;
        return scale;
    }

    /**
     * Modifies {@param xCordinates} to represent a convex border. Fills in all missing values
     * (except on either ends) with appropriate values.
     * @param xCordinates map of x coordinate per y.
     * @param direction 1 for left border and -1 for right border.
     * @param topY the first Y position (inclusive) with a valid value.
     * @param bottomY the last Y position (inclusive) with a valid value.
     */
    private static void convertToConvexArray(
            float[] xCordinates, int direction, int topY, int bottomY) {
        int total = xCordinates.length;
        // The tangent at each pixel.
        float[] angles = new float[total - 1];

        int first = topY; // First valid y coordinate
        int last = -1;    // Last valid y coordinate which didn't have a missing value

        float lastAngle = Float.MAX_VALUE;

        for (int i = topY + 1; i <= bottomY; i++) {
            if (xCordinates[i] <= -1) {
                continue;
            }
            int start;

            if (lastAngle == Float.MAX_VALUE) {
                start = first;
            } else {
                float currentAngle = (xCordinates[i] - xCordinates[last]) / (i - last);
                start = last;
                // If this position creates a concave angle, keep moving up until we find a
                // position which creates a convex angle.
                if ((currentAngle - lastAngle) * direction < 0) {
                    while (start > first) {
                        start --;
                        currentAngle = (xCordinates[i] - xCordinates[start]) / (i - start);
                        if ((currentAngle - angles[start]) * direction >= 0) {
                            break;
                        }
                    }
                }
            }

            // Reset from last check
            lastAngle = (xCordinates[i] - xCordinates[start]) / (i - start);
            // Update all the points from start.
            for (int j = start; j < i; j++) {
                angles[j] = lastAngle;
                xCordinates[j] = xCordinates[start] + lastAngle * (j - start);
            }
            last = i;
        }
    }

    public static IconNormalizer getInstance() {
        synchronized (LOCK) {
            if (sIconNormalizer == null) {
                sIconNormalizer = new IconNormalizer();
            }
        }
        return sIconNormalizer;
    }
}
