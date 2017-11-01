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
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.util.SparseArray;

import com.android.launcher3.graphics.IconPalette;

public class FastBitmapDrawable extends Drawable {

    private static final int[] STATE_PRESSED = new int[] {android.R.attr.state_pressed};

    private static final float PRESSED_BRIGHTNESS = 100f / 255f;
    private static final float DISABLED_DESATURATION = 1f;
    private static final float DISABLED_BRIGHTNESS = 0.5f;

    public static final TimeInterpolator CLICK_FEEDBACK_INTERPOLATOR = new TimeInterpolator() {

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
    public static final int CLICK_FEEDBACK_DURATION = 2000;

    // Since we don't need 256^2 values for combinations of both the brightness and saturation, we
    // reduce the value space to a smaller value V, which reduces the number of cached
    // ColorMatrixColorFilters that we need to keep to V^2
    private static final int REDUCED_FILTER_VALUE_SPACE = 48;

    // A cache of ColorFilters for optimizing brightness and saturation animations
    private static final SparseArray<ColorFilter> sCachedFilter = new SparseArray<>();

    // Temporary matrices used for calculation
    private static final ColorMatrix sTempBrightnessMatrix = new ColorMatrix();
    private static final ColorMatrix sTempFilterMatrix = new ColorMatrix();

    protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Bitmap mBitmap;

    private boolean mIsPressed;
    private boolean mIsDisabled;

    private IconPalette mIconPalette;

    private static final Property<FastBitmapDrawable, Float> BRIGHTNESS
            = new Property<FastBitmapDrawable, Float>(Float.TYPE, "brightness") {
        @Override
        public Float get(FastBitmapDrawable fastBitmapDrawable) {
            return fastBitmapDrawable.getBrightness();
        }

        @Override
        public void set(FastBitmapDrawable fastBitmapDrawable, Float value) {
            fastBitmapDrawable.setBrightness(value);
        }
    };

    // The saturation and brightness are values that are mapped to REDUCED_FILTER_VALUE_SPACE and
    // as a result, can be used to compose the key for the cached ColorMatrixColorFilters
    private int mDesaturation = 0;
    private int mBrightness = 0;
    private int mAlpha = 255;
    private int mPrevUpdateKey = Integer.MAX_VALUE;

    // Animators for the fast bitmap drawable's brightness
    private ObjectAnimator mBrightnessAnimator;

    public FastBitmapDrawable(Bitmap b) {
        mBitmap = b;
        setFilterBitmap(true);
    }

    @Override
    public void draw(Canvas canvas) {
        drawInternal(canvas);
    }

    public void drawWithBrightness(Canvas canvas, float brightness) {
        float oldBrightness = getBrightness();
        setBrightness(brightness);
        drawInternal(canvas);
        setBrightness(oldBrightness);
    }

    protected void drawInternal(Canvas canvas) {
        canvas.drawBitmap(mBitmap, null, getBounds(), mPaint);
    }

    public IconPalette getIconPalette() {
        if (mIconPalette == null) {
            mIconPalette = IconPalette.fromDominantColor(Utilities
                    .findDominantColorByHue(mBitmap, 20), true /* desaturateBackground */);
        }
        return mIconPalette;
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

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean isPressed = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                isPressed = true;
                break;
            }
        }
        if (mIsPressed != isPressed) {
            mIsPressed = isPressed;

            if (mBrightnessAnimator != null) {
                mBrightnessAnimator.cancel();
            }

            if (mIsPressed) {
                // Animate when going to pressed state
                mBrightnessAnimator = ObjectAnimator.ofFloat(
                        this, BRIGHTNESS, getExpectedBrightness());
                mBrightnessAnimator.setDuration(CLICK_FEEDBACK_DURATION);
                mBrightnessAnimator.setInterpolator(CLICK_FEEDBACK_INTERPOLATOR);
                mBrightnessAnimator.start();
            } else {
                setBrightness(getExpectedBrightness());
            }
            return true;
        }
        return false;
    }

    private void invalidateDesaturationAndBrightness() {
        setDesaturation(mIsDisabled ? DISABLED_DESATURATION : 0);
        setBrightness(getExpectedBrightness());
    }

    private float getExpectedBrightness() {
        return mIsDisabled ? DISABLED_BRIGHTNESS :
                (mIsPressed ? PRESSED_BRIGHTNESS : 0);
    }

    public void setIsDisabled(boolean isDisabled) {
        if (mIsDisabled != isDisabled) {
            mIsDisabled = isDisabled;
            invalidateDesaturationAndBrightness();
        }
    }

    /**
     * Sets the saturation of this icon, 0 [full color] -> 1 [desaturated]
     */
    private void setDesaturation(float desaturation) {
        int newDesaturation = (int) Math.floor(desaturation * REDUCED_FILTER_VALUE_SPACE);
        if (mDesaturation != newDesaturation) {
            mDesaturation = newDesaturation;
            updateFilter();
        }
    }

    public float getDesaturation() {
        return (float) mDesaturation / REDUCED_FILTER_VALUE_SPACE;
    }

    /**
     * Sets the brightness of this icon, 0 [no add. brightness] -> 1 [2bright2furious]
     */
    private void setBrightness(float brightness) {
        int newBrightness = (int) Math.floor(brightness * REDUCED_FILTER_VALUE_SPACE);
        if (mBrightness != newBrightness) {
            mBrightness = newBrightness;
            updateFilter();
        }
    }

    private float getBrightness() {
        return (float) mBrightness / REDUCED_FILTER_VALUE_SPACE;
    }

    /**
     * Updates the paint to reflect the current brightness and saturation.
     */
    private void updateFilter() {
        boolean usePorterDuffFilter = false;
        int key = -1;
        if (mDesaturation > 0) {
            key = (mDesaturation << 16) | mBrightness;
        } else if (mBrightness > 0) {
            // Compose a key with a fully saturated icon if we are just animating brightness
            key = (1 << 16) | mBrightness;

            // We found that in L, ColorFilters cause drawing artifacts with shadows baked into
            // icons, so just use a PorterDuff filter when we aren't animating saturation
            usePorterDuffFilter = true;
        }

        // Debounce multiple updates on the same frame
        if (key == mPrevUpdateKey) {
            return;
        }
        mPrevUpdateKey = key;

        if (key != -1) {
            ColorFilter filter = sCachedFilter.get(key);
            if (filter == null) {
                float brightnessF = getBrightness();
                int brightnessI = (int) (255 * brightnessF);
                if (usePorterDuffFilter) {
                    filter = new PorterDuffColorFilter(Color.argb(brightnessI, 255, 255, 255),
                            PorterDuff.Mode.SRC_ATOP);
                } else {
                    float saturationF = 1f - getDesaturation();
                    sTempFilterMatrix.setSaturation(saturationF);
                    if (mBrightness > 0) {
                        // Brightness: C-new = C-old*(1-amount) + amount
                        float scale = 1f - brightnessF;
                        float[] mat = sTempBrightnessMatrix.getArray();
                        mat[0] = scale;
                        mat[6] = scale;
                        mat[12] = scale;
                        mat[4] = brightnessI;
                        mat[9] = brightnessI;
                        mat[14] = brightnessI;
                        sTempFilterMatrix.preConcat(sTempBrightnessMatrix);
                    }
                    filter = new ColorMatrixColorFilter(sTempFilterMatrix);
                }
                sCachedFilter.append(key, filter);
            }
            mPaint.setColorFilter(filter);
        } else {
            mPaint.setColorFilter(null);
        }
        invalidateSelf();
    }
}
