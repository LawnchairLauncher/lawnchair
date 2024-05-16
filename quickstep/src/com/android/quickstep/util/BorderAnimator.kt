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
package com.android.quickstep.util

import android.animation.Animator
import android.annotation.ColorInt
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.animation.Interpolator
import androidx.annotation.Px
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.android.app.animation.Interpolators
import com.android.launcher3.anim.AnimatedFloat
import kotlin.math.roundToInt

/**
 * Utility class for drawing a rounded-rect border around a view.
 *
 * To use this class:
 * 1. Create an instance in the target view. NOTE: The border will animate outwards from the
 *    provided border bounds.
 * 2. Override the target view's [View.draw] method and call [drawBorder] after
 *    `super.draw(canvas)`.
 * 3. Call [buildAnimator] and start the animation or call [setBorderVisibility] where appropriate.
 */
class BorderAnimator
private constructor(
    @field:Px @param:Px private val borderRadiusPx: Int,
    @ColorInt borderColor: Int,
    private val borderAnimationParams: BorderAnimationParams,
    private val appearanceDurationMs: Long,
    private val disappearanceDurationMs: Long,
    private val interpolator: Interpolator,
) {
    private val borderAnimationProgress = AnimatedFloat { updateOutline() }
    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            alpha = 0
        }
    private var runningBorderAnimation: Animator? = null

    companion object {
        const val DEFAULT_BORDER_COLOR = Color.WHITE
        private const val DEFAULT_APPEARANCE_ANIMATION_DURATION_MS = 300L
        private const val DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS = 133L
        private val DEFAULT_INTERPOLATOR = Interpolators.EMPHASIZED_DECELERATE

        /**
         * Creates a BorderAnimator that simply draws the border outside the bound of the target
         * view.
         *
         * Use this method if the border can be drawn outside the target view's bounds without any
         * additional logic.
         *
         * @param borderRadiusPx the radius of the border's corners, in pixels
         * @param borderWidthPx the width of the border, in pixels
         * @param boundsBuilder callback to update the border bounds
         * @param targetView the view that will be drawing the border
         * @param borderColor the border's color
         * @param appearanceDurationMs appearance animation duration, in milliseconds
         * @param disappearanceDurationMs disappearance animation duration, in milliseconds
         * @param interpolator animation interpolator
         */
        @JvmOverloads
        @JvmStatic
        fun createSimpleBorderAnimator(
            @Px borderRadiusPx: Int,
            @Px borderWidthPx: Int,
            boundsBuilder: (Rect) -> Unit,
            targetView: View,
            @ColorInt borderColor: Int = DEFAULT_BORDER_COLOR,
            appearanceDurationMs: Long = DEFAULT_APPEARANCE_ANIMATION_DURATION_MS,
            disappearanceDurationMs: Long = DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS,
            interpolator: Interpolator = DEFAULT_INTERPOLATOR,
        ): BorderAnimator {
            return BorderAnimator(
                borderRadiusPx,
                borderColor,
                SimpleParams(borderWidthPx, boundsBuilder, targetView),
                appearanceDurationMs,
                disappearanceDurationMs,
                interpolator,
            )
        }

        /**
         * Creates a BorderAnimator that scales the target and content views to draw the border
         * within the target's bounds without obscuring the content.
         *
         * Use this method if the border would otherwise be clipped by the target view's bound.
         *
         * Note: using this method will set the scales and pivots of the container and content
         * views, however will only reset the scales back to 1.
         *
         * @param borderRadiusPx the radius of the border's corners, in pixels
         * @param borderWidthPx the width of the border, in pixels
         * @param boundsBuilder callback to update the border bounds
         * @param targetView the view that will be drawing the border
         * @param contentView the view around which the border will be drawn. this view will be
         *   scaled down reciprocally to keep its original size and location.
         * @param borderColor the border's color
         * @param appearanceDurationMs appearance animation duration, in milliseconds
         * @param disappearanceDurationMs disappearance animation duration, in milliseconds
         * @param interpolator animation interpolator
         */
        @JvmOverloads
        @JvmStatic
        fun createScalingBorderAnimator(
            @Px borderRadiusPx: Int,
            @Px borderWidthPx: Int,
            boundsBuilder: (rect: Rect?) -> Unit,
            targetView: View,
            contentView: View,
            @ColorInt borderColor: Int = DEFAULT_BORDER_COLOR,
            appearanceDurationMs: Long = DEFAULT_APPEARANCE_ANIMATION_DURATION_MS,
            disappearanceDurationMs: Long = DEFAULT_DISAPPEARANCE_ANIMATION_DURATION_MS,
            interpolator: Interpolator = DEFAULT_INTERPOLATOR,
        ): BorderAnimator {
            return BorderAnimator(
                borderRadiusPx,
                borderColor,
                ScalingParams(borderWidthPx, boundsBuilder, targetView, contentView),
                appearanceDurationMs,
                disappearanceDurationMs,
                interpolator,
            )
        }
    }

    private fun updateOutline() {
        val interpolatedProgress = interpolator.getInterpolation(borderAnimationProgress.value)
        borderAnimationParams.animationProgress = interpolatedProgress
        borderPaint.alpha = (255 * interpolatedProgress).roundToInt()
        borderPaint.strokeWidth = borderAnimationParams.borderWidth
        borderAnimationParams.targetView.invalidate()
    }

    /**
     * Draws the border on the given canvas.
     *
     * Call this method in the target view's [View.draw] method after calling super.
     */
    fun drawBorder(canvas: Canvas) {
        with(borderAnimationParams) {
            val radius = borderRadiusPx + radiusAdjustment
            canvas.drawRoundRect(
                /* left= */ borderBounds.left + alignmentAdjustment,
                /* top= */ borderBounds.top + alignmentAdjustment,
                /* right= */ borderBounds.right - alignmentAdjustment,
                /* bottom= */ borderBounds.bottom - alignmentAdjustment,
                /* rx= */ radius,
                /* ry= */ radius,
                /* paint= */ borderPaint
            )
        }
    }

    /** Builds the border appearance/disappearance animation. */
    fun buildAnimator(isAppearing: Boolean): Animator {
        return borderAnimationProgress.animateToValue(if (isAppearing) 1f else 0f).apply {
            duration = if (isAppearing) appearanceDurationMs else disappearanceDurationMs
            doOnStart {
                runningBorderAnimation?.cancel()
                runningBorderAnimation = this
                borderAnimationParams.onShowBorder()
            }
            doOnEnd {
                runningBorderAnimation = null
                if (!isAppearing) {
                    borderAnimationParams.onHideBorder()
                }
            }
        }
    }

    /** Shows/hides the border, optionally with an animation. */
    fun setBorderVisibility(visible: Boolean, animated: Boolean) {
        if (animated) {
            buildAnimator(visible).start()
            return
        }
        runningBorderAnimation?.end()
        if (visible) {
            borderAnimationParams.onShowBorder()
        }
        borderAnimationProgress.updateValue(if (visible) 1f else 0f)
        if (!visible) {
            borderAnimationParams.onHideBorder()
        }
    }

    /** Params for handling different target view layout situations. */
    private abstract class BorderAnimationParams(
        @field:Px @param:Px val borderWidthPx: Int,
        private val boundsBuilder: (rect: Rect) -> Unit,
        val targetView: View,
    ) {
        val borderBounds = Rect()
        var animationProgress = 0f
        private var layoutChangeListener: OnLayoutChangeListener? = null

        abstract val alignmentAdjustmentInset: Int
        abstract val radiusAdjustment: Float

        val borderWidth: Float
            get() = borderWidthPx * animationProgress
        val alignmentAdjustment: Float
            // Outset the border by half the width to create an outwards-growth animation
            get() = -borderWidth / 2f + alignmentAdjustmentInset

        open fun onShowBorder() {
            if (layoutChangeListener == null) {
                layoutChangeListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    onShowBorder()
                    targetView.invalidate()
                }
                targetView.addOnLayoutChangeListener(layoutChangeListener)
            }
            boundsBuilder(borderBounds)
        }

        open fun onHideBorder() {
            if (layoutChangeListener != null) {
                targetView.removeOnLayoutChangeListener(layoutChangeListener)
                layoutChangeListener = null
            }
        }
    }

    /** BorderAnimationParams that simply draws the border outside the bounds of the target view. */
    private class SimpleParams(
        @Px borderWidthPx: Int,
        boundsBuilder: (Rect) -> Unit,
        targetView: View,
    ) : BorderAnimationParams(borderWidthPx, boundsBuilder, targetView) {
        override val alignmentAdjustmentInset = 0
        override val radiusAdjustment: Float
            get() = -alignmentAdjustment
    }

    /**
     * BorderAnimationParams that scales the target and content views to draw the border within the
     * target's bounds without obscuring the content.
     */
    private class ScalingParams(
        @Px borderWidthPx: Int,
        boundsBuilder: (rect: Rect?) -> Unit,
        targetView: View,
        private val contentView: View,
    ) : BorderAnimationParams(borderWidthPx, boundsBuilder, targetView) {
        // Inset the border since we are scaling the container up
        override val alignmentAdjustmentInset = borderWidthPx
        override val radiusAdjustment: Float
            // Increase the radius since we are scaling the container up
            get() = alignmentAdjustment

        override fun onShowBorder() {
            super.onShowBorder()
            val tvWidth = targetView.width.toFloat()
            val tvHeight = targetView.height.toFloat()
            // Scale up just enough to make room for the border. Fail fast and fix the scaling
            // onLayout.
            val newScaleX = if (tvWidth == 0f) 1f else 1f + 2 * borderWidthPx / tvWidth
            val newScaleY = if (tvHeight == 0f) 1f else 1f + 2 * borderWidthPx / tvHeight
            with(targetView) {
                pivotX = width / 2f
                pivotY = height / 2f
                scaleX = newScaleX
                scaleY = newScaleY
            }
            with(contentView) {
                pivotX = width / 2f
                pivotY = height / 2f
                scaleX = 1f / newScaleX
                scaleY = 1f / newScaleY
            }
        }

        override fun onHideBorder() {
            super.onHideBorder()
            with(targetView) {
                pivotX = width.toFloat()
                pivotY = height.toFloat()
                scaleX = 1f
                scaleY = 1f
            }
            with(contentView) {
                pivotX = width / 2f
                pivotY = height / 2f
                scaleX = 1f
                scaleY = 1f
            }
        }
    }
}
