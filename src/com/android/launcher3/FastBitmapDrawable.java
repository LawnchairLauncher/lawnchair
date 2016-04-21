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

import android.animation.AnimatorSet;
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
import android.util.SparseArray;
import android.view.animation.DecelerateInterpolator;

public class FastBitmapDrawable extends Drawable {

    /**
     * The possible states that a FastBitmapDrawable can be in.
     */
    public enum State {

        NORMAL                      (0f, 0f, 1f, new DecelerateInterpolator()),
        PRESSED                     (0f, 100f / 255f, 1f, CLICK_FEEDBACK_INTERPOLATOR),
        FAST_SCROLL_HIGHLIGHTED     (0f, 0f, 1.15f, new DecelerateInterpolator()),
        FAST_SCROLL_UNHIGHLIGHTED   (0f, 0f, 1f, new DecelerateInterpolator()),
        DISABLED                    (1f, 0.5f, 1f, new DecelerateInterpolator());

        public final float desaturation;
        public final float brightness;
        /**
         * Used specifically by the view drawing this FastBitmapDrawable.
         */
        public final float viewScale;
        public final TimeInterpolator interpolator;

        State(float desaturation, float brightness, float viewScale, TimeInterpolator interpolator) {
            this.desaturation = desaturation;
            this.brightness = brightness;
            this.viewScale = viewScale;
            this.interpolator = interpolator;
        }
    }

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
    public static final int FAST_SCROLL_HIGHLIGHT_DURATION = 225;
    public static final int FAST_SCROLL_UNHIGHLIGHT_DURATION = 150;
    public static final int FAST_SCROLL_UNHIGHLIGHT_FROM_NORMAL_DURATION = 225;
    public static final int FAST_SCROLL_INACTIVE_DURATION = 275;

    // Since we don't need 256^2 values for combinations of both the brightness and saturation, we
    // reduce the value space to a smaller value V, which reduces the number of cached
    // ColorMatrixColorFilters that we need to keep to V^2
    private static final int REDUCED_FILTER_VALUE_SPACE = 48;

    // A cache of ColorFilters for optimizing brightness and saturation animations
    private static final SparseArray<ColorFilter> sCachedFilter = new SparseArray<>();

    // Temporary matrices used for calculation
    private static final ColorMatrix sTempBrightnessMatrix = new ColorMatrix();
    private static final ColorMatrix sTempFilterMatrix = new ColorMatrix();

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mBitmap;
    private State mState = State.NORMAL;

    // The saturation and brightness are values that are mapped to REDUCED_FILTER_VALUE_SPACE and
    // as a result, can be used to compose the key for the cached ColorMatrixColorFilters
    private int mDesaturation = 0;
    private int mBrightness = 0;
    private int mAlpha = 255;
    private int mPrevUpdateKey = Integer.MAX_VALUE;

    // Animators for the fast bitmap drawable's properties
    private AnimatorSet mPropertyAnimator;

    public FastBitmapDrawable(Bitmap b) {
        mBitmap = b;
        setBounds(0, 0, b.getWidth(), b.getHeight());
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, null, getBounds(), mPaint);
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
     * Animates this drawable to a new state.
     *
     * @return whether the state has changed.
     */
    public boolean animateState(State newState) {
        State prevState = mState;
        if (mState != newState) {
            mState = newState;

            mPropertyAnimator = cancelAnimator(mPropertyAnimator);
            mPropertyAnimator = new AnimatorSet();
            mPropertyAnimator.playTogether(
                    ObjectAnimator
                            .ofFloat(this, "desaturation", newState.desaturation),
                    ObjectAnimator
                            .ofFloat(this, "brightness", newState.brightness));
            mPropertyAnimator.setInterpolator(newState.interpolator);
            mPropertyAnimator.setDuration(getDurationForStateChange(prevState, newState));
            mPropertyAnimator.setStartDelay(getStartDelayForStateChange(prevState, newState));
            mPropertyAnimator.start();
            return true;
        }
        return false;
    }

    /**
     * Immediately sets this drawable to a new state.
     *
     * @return whether the state has changed.
     */
    public boolean setState(State newState) {
        if (mState != newState) {
            mState = newState;

            mPropertyAnimator = cancelAnimator(mPropertyAnimator);

            setDesaturation(newState.desaturation);
            setBrightness(newState.brightness);
            return true;
        }
        return false;
    }

    /**
     * Returns the current state.
     */
    public State getCurrentState() {
        return mState;
    }

    /**
     * Returns the duration for the state change animation.
     */
    public static int getDurationForStateChange(State fromState, State toState) {
        switch (toState) {
            case NORMAL:
                switch (fromState) {
                    case PRESSED:
                        return 0;
                    case FAST_SCROLL_HIGHLIGHTED:
                    case FAST_SCROLL_UNHIGHLIGHTED:
                        return FAST_SCROLL_INACTIVE_DURATION;
                }
            case PRESSED:
                return CLICK_FEEDBACK_DURATION;
            case FAST_SCROLL_HIGHLIGHTED:
                return FAST_SCROLL_HIGHLIGHT_DURATION;
            case FAST_SCROLL_UNHIGHLIGHTED:
                switch (fromState) {
                    case NORMAL:
                        // When animating from normal state, take a little longer
                        return FAST_SCROLL_UNHIGHLIGHT_FROM_NORMAL_DURATION;
                    default:
                        return FAST_SCROLL_UNHIGHLIGHT_DURATION;
                }
        }
        return 0;
    }

    /**
     * Returns the start delay when animating between certain fast scroll states.
     */
    public static int getStartDelayForStateChange(State fromState, State toState) {
        switch (toState) {
            case FAST_SCROLL_UNHIGHLIGHTED:
                switch (fromState) {
                    case NORMAL:
                        return FAST_SCROLL_UNHIGHLIGHT_DURATION / 4;
                }
        }
        return 0;
    }

    /**
     * Sets the saturation of this icon, 0 [full color] -> 1 [desaturated]
     */
    public void setDesaturation(float desaturation) {
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
    public void setBrightness(float brightness) {
        int newBrightness = (int) Math.floor(brightness * REDUCED_FILTER_VALUE_SPACE);
        if (mBrightness != newBrightness) {
            mBrightness = newBrightness;
            updateFilter();
        }
    }

    public float getBrightness() {
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

    private AnimatorSet cancelAnimator(AnimatorSet animator) {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
        return null;
    }
}
