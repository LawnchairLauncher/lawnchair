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

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

public class FastBitmapDrawable extends Drawable {

    static final TimeInterpolator CLICK_FEEDBACK_INTERPOLATOR = new TimeInterpolator() {

        @Override
        public float getInterpolation(float input) {
            if (input < 0.05f) {
                return input / 0.05f;
            } else if (input < 0.3f){
                return 1;
            } else {
                return (1 - input) / 0.7f;
            }
        }
    };
    static final long CLICK_FEEDBACK_DURATION = 2000;

    private static final int PRESSED_BRIGHTNESS = 100;
    private static ColorMatrix sGhostModeMatrix;
    private static final ColorMatrix sTempMatrix = new ColorMatrix();

    /**
     * Store the brightness colors filters to optimize animations during icon press. This
     * only works for non-ghost-mode icons.
     */
    private static final SparseArray<ColorFilter> sCachedBrightnessFilter =
            new SparseArray<ColorFilter>();

    private static final int GHOST_MODE_MIN_COLOR_RANGE = 130;

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mBitmap;
    private int mAlpha;

    private int mBrightness = 0;
    private boolean mGhostModeEnabled = false;

    private boolean mPressed = false;
    private ObjectAnimator mPressedAnimator;

    public FastBitmapDrawable(Bitmap b) {
        mAlpha = 255;
        mBitmap = b;
        setBounds(0, 0, b.getWidth(), b.getHeight());
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect r = getBounds();
        // Draw the bitmap into the bounding rect
        canvas.drawBitmap(mBitmap, null, r, mPaint);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // No op
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setFilterBitmap(boolean filterBitmap) {
        mPaint.setFilterBitmap(filterBitmap);
        mPaint.setAntiAlias(filterBitmap);
    }

    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public int getIntrinsicWidth() {
        return mBitmap.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mBitmap.getHeight();
    }

    @Override
    public int getMinimumWidth() {
        return getBounds().width();
    }

    @Override
    public int getMinimumHeight() {
        return getBounds().height();
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    /**
     * When enabled, the icon is grayed out and the contrast is increased to give it a 'ghost'
     * appearance.
     */
    public void setGhostModeEnabled(boolean enabled) {
        if (mGhostModeEnabled != enabled) {
            mGhostModeEnabled = enabled;
            updateFilter();
        }
    }

    public void setPressed(boolean pressed) {
        if (mPressed != pressed) {
            mPressed = pressed;
            if (mPressed) {
                mPressedAnimator = ObjectAnimator
                        .ofInt(this, "brightness", PRESSED_BRIGHTNESS)
                        .setDuration(CLICK_FEEDBACK_DURATION);
                mPressedAnimator.setInterpolator(CLICK_FEEDBACK_INTERPOLATOR);
                mPressedAnimator.start();
            } else if (mPressedAnimator != null) {
                mPressedAnimator.cancel();
                setBrightness(0);
            }
        }
        invalidateSelf();
    }

    public boolean isGhostModeEnabled() {
        return mGhostModeEnabled;
    }

    public int getBrightness() {
        return mBrightness;
    }

    public void setBrightness(int brightness) {
        if (mBrightness != brightness) {
            mBrightness = brightness;
            updateFilter();
            invalidateSelf();
        }
    }

    private void updateFilter() {
        if (mGhostModeEnabled) {
            if (sGhostModeMatrix == null) {
                sGhostModeMatrix = new ColorMatrix();
                sGhostModeMatrix.setSaturation(0);

                // For ghost mode, set the color range to [GHOST_MODE_MIN_COLOR_RANGE, 255]
                float range = (255 - GHOST_MODE_MIN_COLOR_RANGE) / 255.0f;
                sTempMatrix.set(new float[] {
                        range, 0, 0, 0, GHOST_MODE_MIN_COLOR_RANGE,
                        0, range, 0, 0, GHOST_MODE_MIN_COLOR_RANGE,
                        0, 0, range, 0, GHOST_MODE_MIN_COLOR_RANGE,
                        0, 0, 0, 1, 0 });
                sGhostModeMatrix.preConcat(sTempMatrix);
            }

            if (mBrightness == 0) {
                mPaint.setColorFilter(new ColorMatrixColorFilter(sGhostModeMatrix));
            } else {
                setBrightnessMatrix(sTempMatrix, mBrightness);
                sTempMatrix.postConcat(sGhostModeMatrix);
                mPaint.setColorFilter(new ColorMatrixColorFilter(sTempMatrix));
            }
        } else if (mBrightness != 0) {
            ColorFilter filter = sCachedBrightnessFilter.get(mBrightness);
            if (filter == null) {
                filter = new PorterDuffColorFilter(Color.argb(mBrightness, 255, 255, 255),
                        PorterDuff.Mode.SRC_ATOP);
                sCachedBrightnessFilter.put(mBrightness, filter);
            }
            mPaint.setColorFilter(filter);
        } else {
            mPaint.setColorFilter(null);
        }
    }

    private static void setBrightnessMatrix(ColorMatrix matrix, int brightness) {
        // Brightness: C-new = C-old*(1-amount) + amount
        float scale = 1 - brightness / 255.0f;
        matrix.setScale(scale, scale, scale, 1);
        float[] array = matrix.getArray();

        // Add the amount to RGB components of the matrix, as per the above formula.
        // Fifth elements in the array correspond to the constant being added to
        // red, blue, green, and alpha channel respectively.
        array[4] = brightness;
        array[9] = brightness;
        array[14] = brightness;
    }
}
