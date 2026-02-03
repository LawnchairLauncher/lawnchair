/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shared.statusbar.phone;

import android.annotation.ColorInt;
import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import com.android.app.animation.Interpolators;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class BarTransitions {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_COLORS = false;

    @ColorInt
    private static final int SYSTEM_BAR_BACKGROUND_OPAQUE = Color.BLACK;
    @ColorInt
    private static final int SYSTEM_BAR_BACKGROUND_TRANSPARENT = Color.TRANSPARENT;

    public static final int MODE_TRANSPARENT = 0;
    public static final int MODE_SEMI_TRANSPARENT = 1;
    public static final int MODE_TRANSLUCENT = 2;
    public static final int MODE_LIGHTS_OUT = 3;
    public static final int MODE_OPAQUE = 4;
    public static final int MODE_WARNING = 5;
    public static final int MODE_LIGHTS_OUT_TRANSPARENT = 6;

    @IntDef(flag = true, prefix = { "MODE_" }, value = {
            MODE_OPAQUE,
            MODE_SEMI_TRANSPARENT,
            MODE_TRANSLUCENT,
            MODE_LIGHTS_OUT,
            MODE_TRANSPARENT,
            MODE_WARNING,
            MODE_LIGHTS_OUT_TRANSPARENT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransitionMode {}

    public static final int LIGHTS_IN_DURATION = 250;
    public static final int LIGHTS_OUT_DURATION = 1500;
    public static final int BACKGROUND_DURATION = 200;

    private final String mTag;
    private final View mView;
    protected final BarBackgroundDrawable mBarBackground;

    private @TransitionMode int mMode;
    private boolean mAlwaysOpaque = false;

    public BarTransitions(View view, int gradientResourceId) {
        mTag = "BarTransitions." + view.getClass().getSimpleName();
        mView = view;
        mBarBackground = new BarBackgroundDrawable(mView.getContext(), gradientResourceId);
        mView.setBackground(mBarBackground);
    }

    public void destroy() {
        // To be overridden
    }

    public int getMode() {
        return mMode;
    }

    public void setAutoDim(boolean autoDim) {
        // Default is don't care.
    }

    /**
     * @param alwaysOpaque if {@code true}, the bar's background will always be opaque, regardless
     *         of what mode it is currently set to.
     */
    public void setAlwaysOpaque(boolean alwaysOpaque) {
        mAlwaysOpaque = alwaysOpaque;
    }

    public boolean isAlwaysOpaque() {
        // Low-end devices do not support translucent modes, fallback to opaque
        return mAlwaysOpaque;
    }

    public void transitionTo(int mode, boolean animate) {
        if (isAlwaysOpaque() && (mode == MODE_SEMI_TRANSPARENT || mode == MODE_TRANSLUCENT
                || mode == MODE_TRANSPARENT)) {
            mode = MODE_OPAQUE;
        }
        if (isAlwaysOpaque() && (mode == MODE_LIGHTS_OUT_TRANSPARENT)) {
            mode = MODE_LIGHTS_OUT;
        }
        if (mMode == mode) return;
        int oldMode = mMode;
        mMode = mode;
        if (DEBUG) Log.d(mTag, String.format("%s -> %s animate=%s",
                modeToString(oldMode), modeToString(mode),  animate));
        onTransition(oldMode, mMode, animate);
    }

    protected void onTransition(int oldMode, int newMode, boolean animate) {
        applyModeBackground(oldMode, newMode, animate);
    }

    protected void applyModeBackground(int oldMode, int newMode, boolean animate) {
        if (DEBUG) Log.d(mTag, String.format("applyModeBackground oldMode=%s newMode=%s animate=%s",
                modeToString(oldMode), modeToString(newMode), animate));
        mBarBackground.applyModeBackground(oldMode, newMode, animate);
    }

    public static String modeToString(int mode) {
        if (mode == MODE_OPAQUE) return "MODE_OPAQUE";
        if (mode == MODE_SEMI_TRANSPARENT) return "MODE_SEMI_TRANSPARENT";
        if (mode == MODE_TRANSLUCENT) return "MODE_TRANSLUCENT";
        if (mode == MODE_LIGHTS_OUT) return "MODE_LIGHTS_OUT";
        if (mode == MODE_TRANSPARENT) return "MODE_TRANSPARENT";
        if (mode == MODE_WARNING) return "MODE_WARNING";
        if (mode == MODE_LIGHTS_OUT_TRANSPARENT) return "MODE_LIGHTS_OUT_TRANSPARENT";
        throw new IllegalArgumentException("Unknown mode " + mode);
    }

    public void finishAnimations() {
        mBarBackground.finishAnimation();
    }

    protected boolean isLightsOut(int mode) {
        return mode == MODE_LIGHTS_OUT || mode == MODE_LIGHTS_OUT_TRANSPARENT;
    }

    protected static class BarBackgroundDrawable extends Drawable {
        private final int mOpaque;
        private final int mSemiTransparent;
        private final int mTransparent;
        private final int mWarning;
        private final Drawable mGradient;

        private int mMode = -1;
        private boolean mAnimating;
        private long mStartTime;
        private long mEndTime;

        private int mGradientAlpha;
        private int mColor;
        private float mOverrideAlpha = 1f;
        private PorterDuffColorFilter mTintFilter;
        private Paint mPaint = new Paint();

        private int mGradientAlphaStart;
        private int mColorStart;
        private Rect mFrame;


        public BarBackgroundDrawable(Context context, int gradientResourceId) {
            final Resources res = context.getResources();
            if (DEBUG_COLORS) {
                mOpaque = 0xff0000ff;
                mSemiTransparent = 0x7f0000ff;
                mTransparent = 0x2f0000ff;
                mWarning = 0xffff0000;
            } else {
                mOpaque = SYSTEM_BAR_BACKGROUND_OPAQUE;
                mSemiTransparent = context.getColor(
                        com.android.internal.R.color.system_bar_background_semi_transparent);
                mTransparent = SYSTEM_BAR_BACKGROUND_TRANSPARENT;
                mWarning = getColorAttrDefaultColor(context, android.R.attr.colorError, 0);
            }
            mGradient = context.getDrawable(gradientResourceId);
        }

        public void setFrame(Rect frame) {
            mFrame = frame;
        }

        public void setOverrideAlpha(float overrideAlpha) {
            mOverrideAlpha = overrideAlpha;
            invalidateSelf();
        }

        public float getOverrideAlpha() {
            return mOverrideAlpha;
        }

        public int getColor() {
            return mColor;
        }

        public Rect getFrame() {
            return mFrame;
        }

        @Override
        public void setAlpha(int alpha) {
            // noop
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // noop
        }

        @Override
        public void setTint(int color) {
            PorterDuff.Mode targetMode = mTintFilter == null ? Mode.SRC_IN :
                    mTintFilter.getMode();
            if (mTintFilter == null || mTintFilter.getColor() != color) {
                mTintFilter = new PorterDuffColorFilter(color, targetMode);
            }
            invalidateSelf();
        }

        @Override
        public void setTintMode(Mode tintMode) {
            int targetColor = mTintFilter == null ? 0 : mTintFilter.getColor();
            if (mTintFilter == null || mTintFilter.getMode() != tintMode) {
                mTintFilter = new PorterDuffColorFilter(targetColor, tintMode);
            }
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mGradient.setBounds(bounds);
        }

        public void applyModeBackground(int oldMode, int newMode, boolean animate) {
            if (mMode == newMode) return;
            mMode = newMode;
            mAnimating = animate;
            if (animate) {
                long now = SystemClock.elapsedRealtime();
                mStartTime = now;
                mEndTime = now + BACKGROUND_DURATION;
                mGradientAlphaStart = mGradientAlpha;
                mColorStart = mColor;
            }
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        public void finishAnimation() {
            if (mAnimating) {
                mAnimating = false;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            int targetGradientAlpha = 0, targetColor = 0;
            if (mMode == MODE_WARNING) {
                targetColor = mWarning;
            } else if (mMode == MODE_TRANSLUCENT) {
                targetColor = mSemiTransparent;
            } else if (mMode == MODE_SEMI_TRANSPARENT) {
                targetColor = mSemiTransparent;
            } else if (mMode == MODE_TRANSPARENT || mMode == MODE_LIGHTS_OUT_TRANSPARENT) {
                targetColor = mTransparent;
            } else {
                targetColor = mOpaque;
            }

            if (!mAnimating) {
                mColor = targetColor;
                mGradientAlpha = targetGradientAlpha;
            } else {
                final long now = SystemClock.elapsedRealtime();
                if (now >= mEndTime) {
                    mAnimating = false;
                    mColor = targetColor;
                    mGradientAlpha = targetGradientAlpha;
                } else {
                    final float t = (now - mStartTime) / (float)(mEndTime - mStartTime);
                    final float v = Math.max(0, Math.min(
                            Interpolators.LINEAR.getInterpolation(t), 1));
                    mGradientAlpha = (int)(v * targetGradientAlpha + mGradientAlphaStart * (1 - v));
                    mColor = Color.argb(
                            (int) (v * Color.alpha(targetColor) + Color.alpha(mColorStart) * (1
                                    - v)),
                            (int) (v * Color.red(targetColor) + Color.red(mColorStart) * (1 - v)),
                            (int) (v * Color.green(targetColor) + Color.green(mColorStart) * (1
                                    - v)),
                            (int) (v * Color.blue(targetColor) + Color.blue(mColorStart) * (1
                                    - v)));
                }
            }
            if (mGradientAlpha > 0) {
                mGradient.setAlpha(mGradientAlpha);
                mGradient.draw(canvas);
            }

            if (Color.alpha(mColor) > 0) {
                mPaint.setColor(mColor);
                if (mTintFilter != null) {
                    mPaint.setColorFilter(mTintFilter);
                }
                mPaint.setAlpha((int) (Color.alpha(mColor) * mOverrideAlpha));
                if (mFrame != null) {
                    canvas.drawRect(mFrame, mPaint);
                } else {
                    canvas.drawPaint(mPaint);
                }
            }
            if (mAnimating) {
                invalidateSelf();  // keep going
            }
        }
    }

    /** Get color styled attribute {@code attr}, default to {@code defValue} if not found. */
    @ColorInt
    public static int getColorAttrDefaultColor(Context context, int attr, @ColorInt int defValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[] {attr});
        @ColorInt int colorAccent = ta.getColor(0, defValue);
        ta.recycle();
        return colorAccent;
    }
}
