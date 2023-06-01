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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Px;

import com.android.app.animation.Interpolators;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;

/**
 * Utility class for drawing a rounded-rect border around a view.
 * <p>
 * To use this class:
 * 1. Create an instance in the target view. NOTE: The border will animate outwards from the
 *      provided border bounds. See {@link SimpleParams} and {@link ScalingParams} to determine
 *      which would be best for your target view.
 * 2. Override the target view's {@link android.view.View#draw(Canvas)} method and call
 *      {@link BorderAnimator#drawBorder(Canvas)} after {@code super.draw(canvas)}.
 * 3. Call {@link BorderAnimator#buildAnimator(boolean)} and start the animation or call
 *      {@link BorderAnimator#setBorderVisible(boolean)} where appropriate.
 */
public final class BorderAnimator {

    public static final int DEFAULT_BORDER_COLOR = Color.WHITE;

    private static final long DEFAULT_APPEARANCE_ANIMATION_DURATION_MS = 300;
    private static final long DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS = 133;
    private static final Interpolator DEFAULT_INTERPOLATOR = Interpolators.EMPHASIZED_DECELERATE;

    @NonNull private final AnimatedFloat mBorderAnimationProgress = new AnimatedFloat(
            this::updateOutline);
    @Px private final int mBorderRadiusPx;
    @NonNull private final BorderAnimationParams mBorderAnimationParams;
    private final long mAppearanceDurationMs;
    private final long mDisappearanceDurationMs;
    @NonNull private final Interpolator mInterpolator;
    @NonNull private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable private Animator mRunningBorderAnimation;

    public BorderAnimator(
            @Px int borderRadiusPx,
            @ColorInt int borderColor,
            @NonNull BorderAnimationParams borderAnimationParams) {
        this(borderRadiusPx,
                borderColor,
                borderAnimationParams,
                DEFAULT_APPEARANCE_ANIMATION_DURATION_MS,
                DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS,
                DEFAULT_INTERPOLATOR);
    }

    /**
     * @param borderRadiusPx the radius of the border's corners, in pixels
     * @param borderColor the border's color
     * @param borderAnimationParams params for handling different target view layout situation.
     * @param appearanceDurationMs appearance animation duration, in milliseconds
     * @param disappearanceDurationMs disappearance animation duration, in milliseconds
     * @param interpolator animation interpolator
     */
    public BorderAnimator(
            @Px int borderRadiusPx,
            @ColorInt int borderColor,
            @NonNull BorderAnimationParams borderAnimationParams,
            long appearanceDurationMs,
            long disappearanceDurationMs,
            @NonNull Interpolator interpolator) {
        mBorderRadiusPx = borderRadiusPx;
        mBorderAnimationParams = borderAnimationParams;
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

        mBorderAnimationParams.setProgress(interpolatedProgress);
        mBorderPaint.setAlpha(Math.round(255 * interpolatedProgress));
        mBorderPaint.setStrokeWidth(mBorderAnimationParams.getBorderWidth());
        mBorderAnimationParams.mTargetView.invalidate();
    }

    /**
     * Draws the border on the given canvas.
     * <p>
     * Call this method in the target view's {@link android.view.View#draw(Canvas)} method after
     * calling super.
     */
    public void drawBorder(Canvas canvas) {
        float alignmentAdjustment = mBorderAnimationParams.getAlignmentAdjustment();
        canvas.drawRoundRect(
                /* left= */ mBorderAnimationParams.mBorderBounds.left + alignmentAdjustment,
                /* top= */ mBorderAnimationParams.mBorderBounds.top + alignmentAdjustment,
                /* right= */ mBorderAnimationParams.mBorderBounds.right - alignmentAdjustment,
                /* bottom= */ mBorderAnimationParams.mBorderBounds.bottom - alignmentAdjustment,
                /* rx= */ mBorderRadiusPx + mBorderAnimationParams.getRadiusAdjustment(),
                /* ry= */ mBorderRadiusPx + mBorderAnimationParams.getRadiusAdjustment(),
                /* paint= */ mBorderPaint);
    }

    /**
     * Builds the border appearance/disappearance animation.
     */
    @NonNull
    public Animator buildAnimator(boolean isAppearing) {
        mRunningBorderAnimation = mBorderAnimationProgress.animateToValue(isAppearing ? 1f : 0f);
        mRunningBorderAnimation.setDuration(
                isAppearing ? mAppearanceDurationMs : mDisappearanceDurationMs);

        mRunningBorderAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mBorderAnimationParams.onShowBorder();
            }
        });
        mRunningBorderAnimation.addListener(
                AnimatorListeners.forEndCallback(() -> {
                    mRunningBorderAnimation = null;
                    if (isAppearing) {
                        return;
                    }
                    mBorderAnimationParams.onHideBorder();
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
        if (visible) {
            mBorderAnimationParams.onShowBorder();
        }
        mBorderAnimationProgress.updateValue(visible ? 1f : 0f);
        if (!visible) {
            mBorderAnimationParams.onHideBorder();
        }
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
     * Params for handling different target view layout situation.
     */
    private abstract static class BorderAnimationParams {

        @NonNull private final Rect mBorderBounds = new Rect();
        @NonNull private final BorderBoundsBuilder mBoundsBuilder;

        @NonNull final View mTargetView;
        @Px final int mBorderWidthPx;

        private float mAnimationProgress = 0f;
        @Nullable private View.OnLayoutChangeListener mLayoutChangeListener;

        /**
         * @param borderWidthPx the width of the border, in pixels
         * @param boundsBuilder callback to update the border bounds
         * @param targetView the view that will be drawing the border
         */
        private BorderAnimationParams(
                @Px int borderWidthPx,
                @NonNull BorderBoundsBuilder boundsBuilder,
                @NonNull View targetView) {
            mBorderWidthPx = borderWidthPx;
            mBoundsBuilder = boundsBuilder;
            mTargetView = targetView;
        }

        private void setProgress(float progress) {
            mAnimationProgress = progress;
        }

        private float getBorderWidth() {
            return mBorderWidthPx * mAnimationProgress;
        }

        float getAlignmentAdjustment() {
            // Outset the border by half the width to create an outwards-growth animation
            return (-getBorderWidth() / 2f) + getAlignmentAdjustmentInset();
        }


        void onShowBorder() {
            if (mLayoutChangeListener == null) {
                mLayoutChangeListener =
                        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                            onShowBorder();
                            mTargetView.invalidate();
                        };
                mTargetView.addOnLayoutChangeListener(mLayoutChangeListener);
            }
            mBoundsBuilder.updateBorderBounds(mBorderBounds);
        }

        void onHideBorder() {
            if (mLayoutChangeListener != null) {
                mTargetView.removeOnLayoutChangeListener(mLayoutChangeListener);
                mLayoutChangeListener = null;
            }
        }

        abstract int getAlignmentAdjustmentInset();

        abstract float getRadiusAdjustment();
    }

    /**
     * Use an instance of this {@link BorderAnimationParams} if the border can be drawn outside the
     * target view's bounds without any additional logic.
     */
    public static final class SimpleParams extends BorderAnimationParams {

        public SimpleParams(
                @Px int borderWidthPx,
                @NonNull BorderBoundsBuilder boundsBuilder,
                @NonNull View targetView) {
            super(borderWidthPx, boundsBuilder, targetView);
        }

        @Override
        int getAlignmentAdjustmentInset() {
            return 0;
        }

        @Override
        float getRadiusAdjustment() {
            return -getAlignmentAdjustment();
        }
    }

    /**
     * Use an instance of this {@link BorderAnimationParams} if the border would other be clipped by
     * the target view's bound.
     * <p>
     * Note: using these params will set the scales and pivots of the
     * container and content views, however will only reset the scales back to 1.
     */
    public static final class ScalingParams extends BorderAnimationParams {

        @NonNull private final View mContentView;

        /**
         * @param targetView the view that will be drawing the border. this view will be scaled up
         *                   to make room for the border
         * @param contentView the view around which the border will be drawn. this view will be
         *                    scaled down reciprocally to keep its original size and location.
         */
        public ScalingParams(
                @Px int borderWidthPx,
                @NonNull BorderBoundsBuilder boundsBuilder,
                @NonNull View targetView,
                @NonNull View contentView) {
            super(borderWidthPx, boundsBuilder, targetView);
            mContentView = contentView;
        }

        @Override
        void onShowBorder() {
            super.onShowBorder();
            float width = mTargetView.getWidth();
            float height = mTargetView.getHeight();
            // Scale up just enough to make room for the border. Fail fast and fix the scaling
            // onLayout.
            float scaleX = width == 0 ? 1f : 1f + ((2 * mBorderWidthPx) / width);
            float scaleY = height == 0 ? 1f : 1f + ((2 * mBorderWidthPx) / height);

            mTargetView.setPivotX(width / 2);
            mTargetView.setPivotY(height / 2);
            mTargetView.setScaleX(scaleX);
            mTargetView.setScaleY(scaleY);

            mContentView.setPivotX(mContentView.getWidth() / 2f);
            mContentView.setPivotY(mContentView.getHeight() / 2f);
            mContentView.setScaleX(1f / scaleX);
            mContentView.setScaleY(1f / scaleY);
        }

        @Override
        void onHideBorder() {
            super.onHideBorder();
            mTargetView.setPivotX(mTargetView.getWidth());
            mTargetView.setPivotY(mTargetView.getHeight());
            mTargetView.setScaleX(1f);
            mTargetView.setScaleY(1f);

            mContentView.setPivotX(mContentView.getWidth() / 2f);
            mContentView.setPivotY(mContentView.getHeight() / 2f);
            mContentView.setScaleX(1f);
            mContentView.setScaleY(1f);
        }

        @Override
        int getAlignmentAdjustmentInset() {
            // Inset the border since we are scaling the container up
            return mBorderWidthPx;
        }

        @Override
        float getRadiusAdjustment() {
            // Increase the radius since we are scaling the container up
            return getAlignmentAdjustment();
        }
    }
}
