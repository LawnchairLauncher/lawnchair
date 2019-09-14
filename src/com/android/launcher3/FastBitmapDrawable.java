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

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;

import android.animation.ObjectAnimator;
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
import android.util.Property;
import android.util.SparseArray;

import com.android.launcher3.icons.BitmapInfo;

public class FastBitmapDrawable extends Drawable {

    private static final float PRESSED_SCALE = 1.1f;

    private static final float DISABLED_DESATURATION = 1f;
    private static final float DISABLED_BRIGHTNESS = 0.5f;

    public static final int CLICK_FEEDBACK_DURATION = 200;

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
    protected Bitmap mBitmap;
    protected final int mIconColor;

    private boolean mIsPressed;
    private boolean mIsDisabled;

    // Animator and properties for the fast bitmap drawable's scale
    private static final Property<FastBitmapDrawable, Float> SCALE
            = new Property<FastBitmapDrawable, Float>(Float.TYPE, "scale") {
        @Override
        public Float get(FastBitmapDrawable fastBitmapDrawable) {
            return fastBitmapDrawable.mScale;
        }

        @Override
        public void set(FastBitmapDrawable fastBitmapDrawable, Float value) {
            fastBitmapDrawable.mScale = value;
            fastBitmapDrawable.invalidateSelf();
        }
    };
    private ObjectAnimator mScaleAnimation;
    private float mScale = 1;


    // The saturation and brightness are values that are mapped to REDUCED_FILTER_VALUE_SPACE and
    // as a result, can be used to compose the key for the cached ColorMatrixColorFilters
    private int mDesaturation = 0;
    private int mBrightness = 0;
    private int mAlpha = 255;
    private int mPrevUpdateKey = Integer.MAX_VALUE;

    public FastBitmapDrawable(Bitmap b) {
        this(b, Color.TRANSPARENT);
    }

    public FastBitmapDrawable(BitmapInfo info) {
        this(info.icon, info.color);
    }

    public FastBitmapDrawable(ItemInfoWithIcon info) {
        this(info.iconBitmap, info.iconColor);
    }

    protected FastBitmapDrawable(Bitmap b, int iconColor) {
        this(b, iconColor, false);
    }

    protected FastBitmapDrawable(Bitmap b, int iconColor, boolean isDisabled) {
        mBitmap = b;
        mIconColor = iconColor;
        setFilterBitmap(true);
        setIsDisabled(isDisabled);
    }

    @Override
    public final void draw(Canvas canvas) {
        if (mScale != 1f) {
            int count = canvas.save();
            Rect bounds = getBounds();
            canvas.scale(mScale, mScale, bounds.exactCenterX(), bounds.exactCenterY());
            drawInternal(canvas, bounds);
            canvas.restoreToCount(count);
        } else {
            drawInternal(canvas, getBounds());
        }
    }

    protected void drawInternal(Canvas canvas, Rect bounds) {
        canvas.drawBitmap(mBitmap, null, bounds, mPaint);
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
        if (mAlpha != alpha) {
            mAlpha = alpha;
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setFilterBitmap(boolean filterBitmap) {
        mPaint.setFilterBitmap(filterBitmap);
        mPaint.setAntiAlias(filterBitmap);
    }

    public int getAlpha() {
        return mAlpha;
    }

    public void setScale(float scale) {
        if (mScaleAnimation != null) {
            mScaleAnimation.cancel();
            mScaleAnimation = null;
        }
        mScale = scale;
        invalidateSelf();
    }

    public float getAnimatedScale() {
        return mScaleAnimation == null ? 1 : mScale;
    }

    public float getScale() {
        return mScale;
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

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public ColorFilter getColorFilter() {
        return mPaint.getColorFilter();
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

            if (mScaleAnimation != null) {
                mScaleAnimation.cancel();
                mScaleAnimation = null;
            }

            if (mIsPressed) {
                // Animate when going to pressed state
                mScaleAnimation = ObjectAnimator.ofFloat(this, SCALE, PRESSED_SCALE);
                mScaleAnimation.setDuration(CLICK_FEEDBACK_DURATION);
                mScaleAnimation.setInterpolator(ACCEL);
                mScaleAnimation.start();
            } else {
                if (isVisible()) {
                    mScaleAnimation = ObjectAnimator.ofFloat(this, SCALE, 1f);
                    mScaleAnimation.setDuration(CLICK_FEEDBACK_DURATION);
                    mScaleAnimation.setInterpolator(DEACCEL);
                    mScaleAnimation.start();
                } else {
                    mScale = 1f;
                    invalidateSelf();
                }
            }
            return true;
        }
        return false;
    }

    private void invalidateDesaturationAndBrightness() {
        setDesaturation(mIsDisabled ? DISABLED_DESATURATION : 0);
        setBrightness(mIsDisabled ? DISABLED_BRIGHTNESS : 0);
    }

    public void setIsDisabled(boolean isDisabled) {
        if (mIsDisabled != isDisabled) {
            mIsDisabled = isDisabled;
            invalidateDesaturationAndBrightness();
        }
    }

    protected boolean isDisabled() {
        return mIsDisabled;
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
    protected void updateFilter() {
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

    @Override
    public ConstantState getConstantState() {
        return new MyConstantState(mBitmap, mIconColor, mIsDisabled);
    }

    protected static class MyConstantState extends ConstantState {
        protected final Bitmap mBitmap;
        protected final int mIconColor;
        protected final boolean mIsDisabled;

        public MyConstantState(Bitmap bitmap, int color, boolean isDisabled) {
            mBitmap = bitmap;
            mIconColor = color;
            mIsDisabled = isDisabled;
        }

        @Override
        public Drawable newDrawable() {
            return new FastBitmapDrawable(mBitmap, mIconColor, mIsDisabled);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
