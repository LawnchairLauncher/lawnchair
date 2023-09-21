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
package com.android.quickstep.util;

import android.animation.Animator;
import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Px;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.Interpolators;

/**
 * Utility class for drawing a rounded-rect border around a view.
 * <p>
 * To use this class:
 * 1. Create an instance in the target view.
 * 2. Override the target view's {@link android.view.View#draw(Canvas)} method and call
 *      {@link BorderAnimator#drawBorder(Canvas)} after {@code super.draw(canvas)}.
 * 3. Call {@link BorderAnimator#buildAnimator(boolean)} and start the animation or call
 *      {@link BorderAnimator#setBorderVisible(boolean)} where appropriate.
 */
public final class BorderAnimator {

    public static final int DEFAULT_BORDER_COLOR = 0xffffffff;

    private static final long DEFAULT_APPEARANCE_ANIMATION_DURATION_MS = 300;
    private static final long DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS = 133;
    private static final Interpolator DEFAULT_INTERPOLATOR = Interpolators.EMPHASIZED_DECELERATE;

    @NonNull private final AnimatedFloat mBorderAnimationProgress = new AnimatedFloat(
            this::updateOutline);
    @NonNull private final Rect mBorderBounds = new Rect();
    @NonNull private final BorderBoundsBuilder mBorderBoundsBuilder;
    @Px private final int mBorderWidthPx;
    @Px private final int mBorderRadiusPx;
    @NonNull private final Runnable mInvalidateViewCallback;
    private final long mAppearanceDurationMs;
    private final long mDisappearanceDurationMs;
    @NonNull private final Interpolator mInterpolator;
    @NonNull private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mAlignmentAdjustment;

    @Nullable private Animator mRunningBorderAnimation;

    public BorderAnimator(
            @NonNull BorderBoundsBuilder borderBoundsBuilder,
            int borderWidthPx,
            int borderRadiusPx,
            @ColorInt int borderColor,
            @NonNull Runnable invalidateViewCallback) {
        this(borderBoundsBuilder,
                borderWidthPx,
                borderRadiusPx,
                borderColor,
                invalidateViewCallback,
                DEFAULT_APPEARANCE_ANIMATION_DURATION_MS,
                DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS,
                DEFAULT_INTERPOLATOR);
    }

    public BorderAnimator(
            @NonNull BorderBoundsBuilder borderBoundsBuilder,
            int borderWidthPx,
            int borderRadiusPx,
            @ColorInt int borderColor,
            @NonNull Runnable invalidateViewCallback,
            long appearanceDurationMs,
            long disappearanceDurationMs,
            @NonNull Interpolator interpolator) {
        mBorderBoundsBuilder = borderBoundsBuilder;
        mBorderWidthPx = borderWidthPx;
        mBorderRadiusPx = borderRadiusPx;
        mInvalidateViewCallback = invalidateViewCallback;
        mAppearanceDurationMs = appearanceDurationMs;
        mDisappearanceDurationMs = disappearanceDurationMs;
        mInterpolator = interpolator;

        mBorderPaint.setColor(borderColor);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAlpha(0);
    }

    private void updateOutline() {
        float interpolatedProgress = mInterpolator.getInterpolation(
                mBorderAnimationProgress.value);
        mAlignmentAdjustment = (int) Utilities.mapBoundToRange(
                mBorderAnimationProgress.value,
                /* lowerBound= */ 0f,
                /* upperBound= */ 1f,
                /* toMin= */ 0f,
                /* toMax= */ (float) (mBorderWidthPx / 2f),
                mInterpolator);

        mBorderPaint.setAlpha(Math.round(255 * interpolatedProgress));
        mBorderPaint.setStrokeWidth(Math.round(mBorderWidthPx * interpolatedProgress));
        mInvalidateViewCallback.run();
    }

    /**
     * Draws the border on the given canvas.
     * <p>
     * Call this method in the target view's {@link android.view.View#draw(Canvas)} method after
     * calling super.
     */
    public void drawBorder(Canvas canvas) {
        canvas.drawRoundRect(
                /* left= */ mBorderBounds.left + mAlignmentAdjustment,
                /* top= */ mBorderBounds.top + mAlignmentAdjustment,
                /* right= */ mBorderBounds.right - mAlignmentAdjustment,
                /* bottom= */ mBorderBounds.bottom - mAlignmentAdjustment,
                /* rx= */ mBorderRadiusPx - mAlignmentAdjustment,
                /* ry= */ mBorderRadiusPx - mAlignmentAdjustment,
                /* paint= */ mBorderPaint);
    }

    /**
     * Builds the border appearance/disappearance animation.
     */
    @NonNull
    public Animator buildAnimator(boolean isAppearing) {
        mBorderBoundsBuilder.updateBorderBounds(mBorderBounds);
        mRunningBorderAnimation = mBorderAnimationProgress.animateToValue(isAppearing ? 1f : 0f);
        mRunningBorderAnimation.setDuration(
                isAppearing ? mAppearanceDurationMs : mDisappearanceDurationMs);

        mRunningBorderAnimation.addListener(
                AnimatorListeners.forEndCallback(() -> mRunningBorderAnimation = null));

        return mRunningBorderAnimation;
    }

    /**
     * Immediately shows/hides the border without an animation.
     *
     * To animate the appearance/disappearance, see {@link BorderAnimator#buildAnimator(boolean)}
     */
    public void setBorderVisible(boolean visible) {
        if (mRunningBorderAnimation != null) {
            mRunningBorderAnimation.end();
        }
        mBorderAnimationProgress.updateValue(visible ? 1f : 0f);
    }

    /**
     * Callback to update the border bounds when building this animation.
     */
    public interface BorderBoundsBuilder {

        /**
         * Sets the given rect to the most up-to-date bounds.
         */
        void updateBorderBounds(Rect rect);
    }
}
