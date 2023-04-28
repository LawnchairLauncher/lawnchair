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
import android.animation.AnimatorListenerAdapter;
import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Px;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.Interpolators;

/**
 * Utility class for drawing a rounded-rect border around a view.
 * <p>
 * To use this class:
 * 1. Create an instance in the target view. NOTE: The border will animate outwards from the
 *      provided border bounds. If the border will not be visible outside of those bounds, then a
 *      {@link ViewScaleTargetProvider} must be provided in the constructor.
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
    @Nullable private final ViewScaleTargetProvider mViewScaleTargetProvider;
    private final long mAppearanceDurationMs;
    private final long mDisappearanceDurationMs;
    @NonNull private final Interpolator mInterpolator;
    @NonNull private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float mAlignmentAdjustment;

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
                /* viewScaleTargetProvider= */ null);
    }

    public BorderAnimator(
            @NonNull BorderBoundsBuilder borderBoundsBuilder,
            int borderWidthPx,
            int borderRadiusPx,
            @ColorInt int borderColor,
            @NonNull Runnable invalidateViewCallback,
            @Nullable ViewScaleTargetProvider viewScaleTargetProvider) {
        this(borderBoundsBuilder,
                borderWidthPx,
                borderRadiusPx,
                borderColor,
                invalidateViewCallback,
                viewScaleTargetProvider,
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
            @Nullable ViewScaleTargetProvider viewScaleTargetProvider,
            long appearanceDurationMs,
            long disappearanceDurationMs,
            @NonNull Interpolator interpolator) {
        mBorderBoundsBuilder = borderBoundsBuilder;
        mBorderWidthPx = borderWidthPx;
        mBorderRadiusPx = borderRadiusPx;
        mInvalidateViewCallback = invalidateViewCallback;
        mViewScaleTargetProvider = viewScaleTargetProvider;
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
        float borderWidth = mBorderWidthPx * interpolatedProgress;
        // Outset the border by half the width to create an outwards-growth animation
        mAlignmentAdjustment = (-borderWidth / 2f)
                // Inset the border if we are scaling the container up
                + (mViewScaleTargetProvider == null ? 0 : mBorderWidthPx);

        mBorderPaint.setAlpha(Math.round(255 * interpolatedProgress));
        mBorderPaint.setStrokeWidth(borderWidth);
        mInvalidateViewCallback.run();
    }

    /**
     * Draws the border on the given canvas.
     * <p>
     * Call this method in the target view's {@link android.view.View#draw(Canvas)} method after
     * calling super.
     */
    public void drawBorder(Canvas canvas) {
        // Increase the radius if we are scaling the container up
        float radiusAdjustment = mViewScaleTargetProvider == null
                ? -mAlignmentAdjustment : mAlignmentAdjustment;
        canvas.drawRoundRect(
                /* left= */ mBorderBounds.left + mAlignmentAdjustment,
                /* top= */ mBorderBounds.top + mAlignmentAdjustment,
                /* right= */ mBorderBounds.right - mAlignmentAdjustment,
                /* bottom= */ mBorderBounds.bottom - mAlignmentAdjustment,
                /* rx= */ mBorderRadiusPx + radiusAdjustment,
                /* ry= */ mBorderRadiusPx + radiusAdjustment,
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

        mRunningBorderAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setViewScales();
            }
        });
        mRunningBorderAnimation.addListener(
                AnimatorListeners.forEndCallback(() -> {
                    mRunningBorderAnimation = null;
                    if (isAppearing) {
                        return;
                    }
                    resetViewScales();
                }));

        return mRunningBorderAnimation;
    }

    /**
     * Immediately shows/hides the border without an animation.
     * <p>
     * To animate the appearance/disappearance, see {@link BorderAnimator#buildAnimator(boolean)}
     */
    public void setBorderVisible(boolean visible) {
        if (mRunningBorderAnimation != null) {
            mRunningBorderAnimation.end();
        }
        mBorderBoundsBuilder.updateBorderBounds(mBorderBounds);
        if (visible) {
            setViewScales();
        }
        mBorderAnimationProgress.updateValue(visible ? 1f : 0f);
        if (!visible) {
            resetViewScales();
        }
    }

    private void setViewScales() {
        if (mViewScaleTargetProvider == null) {
            return;
        }
        View container = mViewScaleTargetProvider.getContainerView();
        float width = container.getWidth();
        float height = container.getHeight();
        // scale up just enough to make room for the border
        float scaleX = 1f + ((2 * mBorderWidthPx) / width);
        float scaleY = 1f + ((2 * mBorderWidthPx) / height);

        container.setPivotX(width / 2);
        container.setPivotY(height / 2);
        container.setScaleX(scaleX);
        container.setScaleY(scaleY);

        View contentView = mViewScaleTargetProvider.getContentView();
        contentView.setPivotX(contentView.getWidth() / 2f);
        contentView.setPivotY(contentView.getHeight() / 2f);
        contentView.setScaleX(1f / scaleX);
        contentView.setScaleY(1f / scaleY);
    }

    private void resetViewScales() {
        if (mViewScaleTargetProvider == null) {
            return;
        }
        View container = mViewScaleTargetProvider.getContainerView();
        container.setPivotX(container.getWidth());
        container.setPivotY(container.getHeight());
        container.setScaleX(1f);
        container.setScaleY(1f);

        View contentView = mViewScaleTargetProvider.getContentView();
        contentView.setPivotX(contentView.getWidth() / 2f);
        contentView.setPivotY(contentView.getHeight() / 2f);
        contentView.setScaleX(1f);
        contentView.setScaleY(1f);
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

    /**
     * Provider for scaling target views for the beginning and end of this animation.
     */
    public interface ViewScaleTargetProvider {

        /**
         * Returns the content view's container. This view will be scaled up to make room for the
         * border.
         */
        @NonNull
        View getContainerView();

        /**
         * Returns the content view. This view will be scaled down reciprocally to the container's
         * up-scaling to maintain its original size. This should be the view containing all of the
         * content being surrounded by the border.
         */
        @NonNull
        View getContentView();
    }
}
