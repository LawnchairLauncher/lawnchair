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

package com.android.launcher3.icons;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class IconNormalizer {

    private static final String TAG = "IconNormalizer";
    private static final boolean DEBUG = false;
    // Ratio of icon visible area to full icon size for a square shaped icon
    private static final float MAX_SQUARE_AREA_FACTOR = 375.0f / 576;
    // Ratio of icon visible area to full icon size for a circular shaped icon
    private static final float MAX_CIRCLE_AREA_FACTOR = 380.0f / 576;

    private static final float CIRCLE_AREA_BY_RECT = (float) Math.PI / 4;

    // Slope used to calculate icon visible area to full icon size for any generic shaped icon.
    private static final float LINEAR_SCALE_SLOPE =
            (MAX_CIRCLE_AREA_FACTOR - MAX_SQUARE_AREA_FACTOR) / (1 - CIRCLE_AREA_BY_RECT);

    private static final int MIN_VISIBLE_ALPHA = 40;

    // Shape detection related constants
    private static final float BOUND_RATIO_MARGIN = .05f;
    private static final float PIXEL_DIFF_PERCENTAGE_THRESHOLD = 0.005f;
    private static final float SCALE_NOT_INITIALIZED = 0;

    // Ratio of the diameter of an normalized circular icon to the actual icon size.
    public static final float ICON_VISIBLE_AREA_FACTOR = 0.92f;

    private final int mMaxSize;
    private final Bitmap mBitmap;
    private final Canvas mCanvas;
    private final Paint mPaintMaskShape;
    private final Paint mPaintMaskShapeOutline;
    private final byte[] mPixels;

    private final RectF mAdaptiveIconBounds;
    private float mAdaptiveIconScale;

    private boolean mEnableShapeDetection;

    // for each y, stores the position of the leftmost x and the rightmost x
    private final float[] mLeftBorder;
    private final float[] mRightBorder;
    private final Rect mBounds;
    private final Path mShapePath;
    private final Matrix mMatrix;

    /** package private **/
    IconNormalizer(Context context, int iconBitmapSize, boolean shapeDetection) {
        // Use twice the icon size as maximum size to avoid scaling down twice.
        mMaxSize = iconBitmapSize * 2;
        mBitmap = Bitmap.createBitmap(mMaxSize, mMaxSize, Bitmap.Config.ALPHA_8);
        mCanvas = new Canvas(mBitmap);
        mPixels = new byte[mMaxSize * mMaxSize];
        mLeftBorder = new float[mMaxSize];
        mRightBorder = new float[mMaxSize];
        mBounds = new Rect();
        mAdaptiveIconBounds = new RectF();

        mPaintMaskShape = new Paint();
        mPaintMaskShape.setColor(Color.RED);
        mPaintMaskShape.setStyle(Paint.Style.FILL);
        mPaintMaskShape.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));

        mPaintMaskShapeOutline = new Paint();
        mPaintMaskShapeOutline.setStrokeWidth(
                2 * context.getResources().getDisplayMetrics().density);
        mPaintMaskShapeOutline.setStyle(Paint.Style.STROKE);
        mPaintMaskShapeOutline.setColor(Color.BLACK);
        mPaintMaskShapeOutline.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mShapePath = new Path();
        mMatrix = new Matrix();
        mAdaptiveIconScale = SCALE_NOT_INITIALIZED;
        mEnableShapeDetection = shapeDetection;
    }

    private static float getScale(float hullArea, float boundingArea, float fullArea) {
        float hullByRect = hullArea / boundingArea;
        float scaleRequired;
        if (hullByRect < CIRCLE_AREA_BY_RECT) {
            scaleRequired = MAX_CIRCLE_AREA_FACTOR;
        } else {
            scaleRequired = MAX_SQUARE_AREA_FACTOR + LINEAR_SCALE_SLOPE * (1 - hullByRect);
        }

        float areaScale = hullArea / fullArea;
        // Use sqrt of the final ratio as the images is scaled across both width and height.
        return areaScale > scaleRequired ? (float) Math.sqrt(scaleRequired / areaScale) : 1;
    }

    /**
     * @param d Should be AdaptiveIconDrawable
     * @param size Canvas size to use
     */
    @TargetApi(Build.VERSION_CODES.O)
    public static float normalizeAdaptiveIcon(Drawable d, int size, @Nullable RectF outBounds) {
        Rect tmpBounds = new Rect(d.getBounds());
        d.setBounds(0, 0, size, size);

        Path path = ((AdaptiveIconDrawable) d).getIconMask();
        Region region = new Region();
        region.setPath(path, new Region(0, 0, size, size));

        Rect hullBounds = region.getBounds();
        int hullArea = GraphicsUtils.getArea(region);

        if (outBounds != null) {
            float sizeF = size;
            outBounds.set(
                    hullBounds.left / sizeF,
                    hullBounds.top / sizeF,
                    1 - (hullBounds.right / sizeF),
                    1 - (hullBounds.bottom / sizeF));
        }
        d.setBounds(tmpBounds);
        return getScale(hullArea, hullArea, size * size);
    }

    /**
     * Returns if the shape of the icon is same as the path.
     * For this method to work, the shape path bounds should be in [0,1]x[0,1] bounds.
     */
    private boolean isShape(Path maskPath) {
        // Condition1:
        // If width and height of the path not close to a square, then the icon shape is
        // not same as the mask shape.
        float iconRatio = ((float) mBounds.width()) / mBounds.height();
        if (Math.abs(iconRatio - 1) > BOUND_RATIO_MARGIN) {
            if (DEBUG) {
                Log.d(TAG, "Not same as mask shape because width != height. " + iconRatio);
            }
            return false;
        }

        // Condition 2:
        // Actual icon (white) and the fitted shape (e.g., circle)(red) XOR operation
        // should generate transparent image, if the actual icon is equivalent to the shape.

        // Fit the shape within the icon's bounding box
        mMatrix.reset();
        mMatrix.setScale(mBounds.width(), mBounds.height());
        mMatrix.postTranslate(mBounds.left, mBounds.top);
        maskPath.transform(mMatrix, mShapePath);

        // XOR operation
        mCanvas.drawPath(mShapePath, mPaintMaskShape);

        // DST_OUT operation around the mask path outline
        mCanvas.drawPath(mShapePath, mPaintMaskShapeOutline);

        // Check if the result is almost transparent
        return isTransparentBitmap();
    }

    /**
     * Used to determine if certain the bitmap is transparent.
     */
    private boolean isTransparentBitmap() {
        ByteBuffer buffer = ByteBuffer.wrap(mPixels);
        buffer.rewind();
        mBitmap.copyPixelsToBuffer(buffer);

        int y = mBounds.top;
        // buffer position
        int index = y * mMaxSize;
        // buffer shift after every row, width of buffer = mMaxSize
        int rowSizeDiff = mMaxSize - mBounds.right;

        int sum = 0;
        for (; y < mBounds.bottom; y++) {
            index += mBounds.left;
            for (int x = mBounds.left; x < mBounds.right; x++) {
                if ((mPixels[index] & 0xFF) > MIN_VISIBLE_ALPHA) {
                    sum++;
                }
                index++;
            }
            index += rowSizeDiff;
        }

        float percentageDiffPixels = ((float) sum) / (mBounds.width() * mBounds.height());
        return percentageDiffPixels < PIXEL_DIFF_PERCENTAGE_THRESHOLD;
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
     *
     * @param outBounds optional rect to receive the fraction distance from each edge.
     */
    public synchronized float getScale(@NonNull Drawable d, @Nullable RectF outBounds,
            @Nullable Path path, @Nullable boolean[] outMaskShape) {
        if (BaseIconFactory.ATLEAST_OREO && d instanceof AdaptiveIconDrawable) {
            if (mAdaptiveIconScale == SCALE_NOT_INITIALIZED) {
                mAdaptiveIconScale = normalizeAdaptiveIcon(d, mMaxSize, mAdaptiveIconBounds);
            }
            if (outBounds != null) {
                outBounds.set(mAdaptiveIconBounds);
            }
            return mAdaptiveIconScale;
        }
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

        mBounds.left = leftX;
        mBounds.right = rightX;

        mBounds.top = topY;
        mBounds.bottom = bottomY;

        if (outBounds != null) {
            outBounds.set(((float) mBounds.left) / width, ((float) mBounds.top) / height,
                    1 - ((float) mBounds.right) / width,
                    1 - ((float) mBounds.bottom) / height);
        }
        if (outMaskShape != null && mEnableShapeDetection && outMaskShape.length > 0) {
            outMaskShape[0] = isShape(path);
        }
        // Area of the rectangle required to fit the convex hull
        float rectArea = (bottomY + 1 - topY) * (rightX + 1 - leftX);
        return getScale(area, rectArea, width * height);
    }

    /**
     * Modifies {@param xCoordinates} to represent a convex border. Fills in all missing values
     * (except on either ends) with appropriate values.
     * @param xCoordinates map of x coordinate per y.
     * @param direction 1 for left border and -1 for right border.
     * @param topY the first Y position (inclusive) with a valid value.
     * @param bottomY the last Y position (inclusive) with a valid value.
     */
    private static void convertToConvexArray(
            float[] xCoordinates, int direction, int topY, int bottomY) {
        int total = xCoordinates.length;
        // The tangent at each pixel.
        float[] angles = new float[total - 1];

        int first = topY; // First valid y coordinate
        int last = -1;    // Last valid y coordinate which didn't have a missing value

        float lastAngle = Float.MAX_VALUE;

        for (int i = topY + 1; i <= bottomY; i++) {
            if (xCoordinates[i] <= -1) {
                continue;
            }
            int start;

            if (lastAngle == Float.MAX_VALUE) {
                start = first;
            } else {
                float currentAngle = (xCoordinates[i] - xCoordinates[last]) / (i - last);
                start = last;
                // If this position creates a concave angle, keep moving up until we find a
                // position which creates a convex angle.
                if ((currentAngle - lastAngle) * direction < 0) {
                    while (start > first) {
                        start --;
                        currentAngle = (xCoordinates[i] - xCoordinates[start]) / (i - start);
                        if ((currentAngle - angles[start]) * direction >= 0) {
                            break;
                        }
                    }
                }
            }

            // Reset from last check
            lastAngle = (xCoordinates[i] - xCoordinates[start]) / (i - start);
            // Update all the points from start.
            for (int j = start; j < i; j++) {
                angles[j] = lastAngle;
                xCoordinates[j] = xCoordinates[start] + lastAngle * (j - start);
            }
            last = i;
        }
    }

    /**
     * @return The diameter of the normalized circle that fits inside of the square (size x size).
     */
    public static int getNormalizedCircleSize(int size) {
        float area = size * size * MAX_CIRCLE_AREA_FACTOR;
        return (int) Math.round(Math.sqrt((4 * area) / Math.PI));
    }
}
