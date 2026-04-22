/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles.bar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;
import androidx.core.animation.IntProperty;
import androidx.core.content.ContextCompat;

import com.android.wm.shell.R;

/**
 * Handle view to show at the top of a bubble bar expanded view.
 */
public class BubbleBarHandleView extends View {
    private static final long COLOR_CHANGE_DURATION = 120;

    /** Custom property to set handle color. */
    private static final IntProperty<BubbleBarHandleView> HANDLE_COLOR = new IntProperty<>(
            "handleColor") {
        @Override
        public void setValue(BubbleBarHandleView bubbleBarHandleView, int color) {
            bubbleBarHandleView.setHandleColor(color);
        }

        @Override
        public Integer get(BubbleBarHandleView bubbleBarHandleView) {
            return bubbleBarHandleView.getHandleColor();
        }
    };

    @VisibleForTesting
    final Paint mHandlePaint = new Paint();
    private final @ColorInt int mHandleLightColor;
    private final @ColorInt int mHandleDarkColor;
    private final ArgbEvaluator mArgbEvaluator = ArgbEvaluator.getInstance();
    private final float mHandleHeight;
    private final float mHandleWidth;
    private float mCurrentHandleHeight;
    private float mCurrentHandleWidth;
    @Nullable
    private ObjectAnimator mColorChangeAnim;
    private @ColorInt int mRegionSamplerColor;
    private boolean mHasSampledColor;

    public BubbleBarHandleView(Context context) {
        this(context, null /* attrs */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0 /* defStyleRes */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mHandlePaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mHandlePaint.setStyle(Paint.Style.FILL);
        mHandlePaint.setColor(0);
        mHandleHeight = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_handle_height);
        mHandleWidth = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_caption_width);
        mHandleLightColor = ContextCompat.getColor(getContext(),
                R.color.bubble_bar_expanded_view_handle_light);
        mHandleDarkColor = ContextCompat.getColor(getContext(),
                R.color.bubble_bar_expanded_view_handle_dark);
        mCurrentHandleHeight = mHandleHeight;
        mCurrentHandleWidth = mHandleWidth;
        setContentDescription(getResources().getString(R.string.handle_text));
    }

    private void setHandleColor(int color) {
        mHandlePaint.setColor(color);
        invalidate();
    }

    /**
     * Get current color value for the handle
     */
    @ColorInt
    public int getHandleColor() {
        return mHandlePaint.getColor();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float handleLeft = (getWidth() - mCurrentHandleWidth) / 2;
        float handleRight = handleLeft + mCurrentHandleWidth;
        float handleCenterY = (float) getHeight() / 2;
        float handleTop = (int) (handleCenterY - mCurrentHandleHeight / 2);
        float handleBottom = handleTop + mCurrentHandleHeight;
        float cornerRadius = mCurrentHandleHeight / 2;
        canvas.drawRoundRect(handleLeft, handleTop, handleRight, handleBottom, cornerRadius,
                cornerRadius, mHandlePaint);
    }

    /** Sets handle width, height and color. Does not change the layout properties */
    private void setHandleProperties(float width, float height, int color) {
        mCurrentHandleHeight = height;
        mCurrentHandleWidth = width;
        mHandlePaint.setColor(color);
        invalidate();
    }

    /**
     * Set initial color for the handle. Takes effect if the
     * {@link #updateHandleColor(boolean, boolean)} has not been called.
     */
    public void setHandleInitialColor(@ColorInt int color) {
        if (!mHasSampledColor) {
            setHandleColor(color);
        }
    }

    /**
     * Updates the handle color.
     *
     * @param isRegionDark Whether the background behind the handle is dark, and thus the handle
     *                     should be light (and vice versa).
     * @param animated     Whether to animate the change, or apply it immediately.
     */
    public void updateHandleColor(boolean isRegionDark, boolean animated) {
        int newColor = isRegionDark ? mHandleLightColor : mHandleDarkColor;
        if (newColor == mRegionSamplerColor) {
            return;
        }
        mHasSampledColor = true;
        mRegionSamplerColor = newColor;
        if (mColorChangeAnim != null) {
            mColorChangeAnim.cancel();
        }
        if (animated) {
            mColorChangeAnim = ObjectAnimator.ofArgb(this, HANDLE_COLOR, newColor);
            mColorChangeAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mColorChangeAnim = null;
                }
            });
            mColorChangeAnim.setDuration(COLOR_CHANGE_DURATION);
            mColorChangeAnim.start();
        } else {
            setHandleColor(newColor);
        }
    }

    /** Returns handle padding top. */
    public int getHandlePaddingTop() {
        return (getHeight() - getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_handle_height)) / 2;
    }

    /** Animates handle for the bubble menu. */
    public void animateHandleForMenu(float progress, float widthDelta, float heightDelta,
            int menuColor) {
        float currentWidth = mHandleWidth + widthDelta * progress;
        float currentHeight = mHandleHeight + heightDelta * progress;
        int color = (int) mArgbEvaluator.evaluate(progress, mRegionSamplerColor, menuColor);
        setHandleProperties(currentWidth, currentHeight, color);
        setTranslationY(heightDelta * progress / 2);
    }

    /** Restores all the properties that were animated to the default values. */
    public void restoreAnimationDefaults() {
        setHandleProperties(mHandleWidth, mHandleHeight, mRegionSamplerColor);
        setTranslationY(0);
    }

    /** Returns the handle height. */
    public int getHandleHeight() {
        return (int) mHandleHeight;
    }

    /** Returns the handle width. */
    public int getHandleWidth() {
        return (int) mHandleWidth;
    }
}
