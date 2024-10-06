/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.mapRange
import com.android.launcher3.Utilities.mapToRange
import com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound
import com.android.launcher3.taskbar.TaskbarPinningController.Companion.PINNING_PERSISTENT
import com.android.launcher3.taskbar.TaskbarPinningController.Companion.PINNING_TRANSIENT
import com.android.launcher3.util.DisplayController
import kotlin.math.min

/** Helps draw the taskbar background, made up of a rectangle plus two inverted rounded corners. */
class TaskbarBackgroundRenderer(private val context: TaskbarActivityContext) {

    private val isInSetup: Boolean = !context.isUserSetupComplete

    private val maxTransientTaskbarHeight =
        context.transientTaskbarDeviceProfile.taskbarHeight.toFloat()
    private val maxPersistentTaskbarHeight =
        context.persistentTaskbarDeviceProfile.taskbarHeight.toFloat()
    var backgroundProgress =
        if (DisplayController.isTransientTaskbar(context)) {
            PINNING_TRANSIENT
        } else {
            PINNING_PERSISTENT
        }

    var isAnimatingPinning = false

    val paint = Paint()
    private val strokePaint = Paint()
    val lastDrawnTransientRect = RectF()
    var backgroundHeight = context.deviceProfile.taskbarHeight.toFloat()
    var translationYForSwipe = 0f
    var translationYForStash = 0f

    private val transientBackgroundBounds = context.transientTaskbarBounds

    private val shadowAlpha: Float
    private val strokeAlpha: Int
    private var shadowBlur = 0f
    private var keyShadowDistance = 0f
    private var bottomMargin = 0

    private val fullCornerRadius = context.cornerRadius.toFloat()
    private var cornerRadius = fullCornerRadius
    private var widthInsetPercentage = 0f
    private val square = Path()
    private val circle = Path()
    private val invertedLeftCornerPath = Path()
    private val invertedRightCornerPath = Path()

    private var stashedHandleWidth =
        context.resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_width)

    private val stashedHandleHeight =
        context.resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_height)

    init {
        paint.color = context.getColor(R.color.taskbar_background)
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.FILL
        strokePaint.color = context.getColor(R.color.taskbar_stroke)
        strokePaint.flags = Paint.ANTI_ALIAS_FLAG
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth =
            context.resources.getDimension(R.dimen.transient_taskbar_stroke_width)
        if (Utilities.isDarkTheme(context)) {
            strokeAlpha = DARK_THEME_STROKE_ALPHA
            shadowAlpha = DARK_THEME_SHADOW_ALPHA
        } else {
            strokeAlpha = LIGHT_THEME_STROKE_ALPHA
            shadowAlpha = LIGHT_THEME_SHADOW_ALPHA
        }

        setCornerRoundness(DEFAULT_ROUNDNESS)
    }

    fun updateStashedHandleWidth(context: TaskbarActivityContext, res: Resources) {
        stashedHandleWidth =
            res.getDimensionPixelSize(
                if (context.isPhoneMode || context.isTinyTaskbar) {
                    R.dimen.taskbar_stashed_small_screen
                } else {
                    R.dimen.taskbar_stashed_handle_width
                }
            )
    }

    /**
     * Sets the roundness of the round corner above Taskbar. No effect on transient Taskbar.
     *
     * @param cornerRoundness 0 has no round corner, 1 has complete round corner.
     */
    fun setCornerRoundness(cornerRoundness: Float) {
        if (DisplayController.isTransientTaskbar(context) && !transientBackgroundBounds.isEmpty) {
            return
        }

        cornerRadius = fullCornerRadius * cornerRoundness

        // Create the paths for the inverted rounded corners above the taskbar. Start with a filled
        // square, and then subtract out a circle from the appropriate corner.
        square.reset()
        square.addRect(0f, 0f, cornerRadius, cornerRadius, Path.Direction.CW)
        circle.reset()
        circle.addCircle(cornerRadius, 0f, cornerRadius, Path.Direction.CW)
        invertedLeftCornerPath.op(square, circle, Path.Op.DIFFERENCE)

        circle.reset()
        circle.addCircle(0f, 0f, cornerRadius, Path.Direction.CW)
        invertedRightCornerPath.op(square, circle, Path.Op.DIFFERENCE)
    }

    /** Draws the background with the given paint and height, on the provided canvas. */
    fun draw(canvas: Canvas) {
        if (isInSetup) return
        val isTransientTaskbar = backgroundProgress == 0f
        canvas.save()
        if (!isTransientTaskbar || transientBackgroundBounds.isEmpty || isAnimatingPinning) {
            drawPersistentBackground(canvas)
        }
        canvas.restore()
        canvas.save()
        if (isAnimatingPinning || isTransientTaskbar) {
            drawTransientBackground(canvas)
        }
        canvas.restore()
    }

    private fun drawPersistentBackground(canvas: Canvas) {
        if (isAnimatingPinning) {
            val persistentTaskbarHeight = maxPersistentTaskbarHeight * backgroundProgress
            canvas.translate(0f, canvas.height - persistentTaskbarHeight)
            // Draw the background behind taskbar content.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), persistentTaskbarHeight, paint)
        } else {
            val persistentTaskbarHeight = min(maxPersistentTaskbarHeight, backgroundHeight)
            canvas.translate(0f, canvas.height - persistentTaskbarHeight)
            // Draw the background behind taskbar content.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), persistentTaskbarHeight, paint)
        }

        // Draw the inverted rounded corners above the taskbar.
        canvas.translate(0f, -cornerRadius)
        canvas.drawPath(invertedLeftCornerPath, paint)
        canvas.translate(0f, cornerRadius)
        canvas.translate(canvas.width - cornerRadius, -cornerRadius)
        canvas.drawPath(invertedRightCornerPath, paint)
    }

    private fun drawTransientBackground(canvas: Canvas) {
        val res = context.resources
        val transientTaskbarHeight = maxTransientTaskbarHeight * (1f - backgroundProgress)
        val heightProgressWhileAnimating =
            if (isAnimatingPinning) transientTaskbarHeight else backgroundHeight

        var progress = heightProgressWhileAnimating / maxTransientTaskbarHeight
        progress = Math.round(progress * 100f) / 100f
        if (isAnimatingPinning) {
            var scale = transientTaskbarHeight / maxTransientTaskbarHeight
            scale = Math.round(scale * 100f) / 100f
            bottomMargin =
                mapRange(
                        scale,
                        0f,
                        res.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin).toFloat()
                    )
                    .toInt()
            shadowBlur =
                mapRange(scale, 0f, res.getDimension(R.dimen.transient_taskbar_shadow_blur))
            keyShadowDistance =
                mapRange(scale, 0f, res.getDimension(R.dimen.transient_taskbar_key_shadow_distance))
        } else {
            bottomMargin = res.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin)
            shadowBlur = res.getDimension(R.dimen.transient_taskbar_shadow_blur)
            keyShadowDistance = res.getDimension(R.dimen.transient_taskbar_key_shadow_distance)
        }

        // At progress 0, we draw the background as the stashed handle.
        // At progress 1, we draw the background as the full taskbar.
        // Min height capped to max persistent taskbar height for animation
        val backgroundHeightWhileAnimating =
            if (isAnimatingPinning) maxPersistentTaskbarHeight else stashedHandleHeight.toFloat()
        val newBackgroundHeight =
            mapRange(progress, backgroundHeightWhileAnimating, maxTransientTaskbarHeight)
        val fullWidth = transientBackgroundBounds.width()
        val animationWidth = context.currentTaskbarWidth
        val backgroundWidthWhileAnimating =
            if (isAnimatingPinning) animationWidth else stashedHandleWidth.toFloat()

        val newWidth = mapRange(progress, backgroundWidthWhileAnimating, fullWidth.toFloat())
        val halfWidthDelta = (fullWidth - newWidth) / 2f
        val radius = newBackgroundHeight / 2f
        val bottomMarginProgress = bottomMargin * ((1f - progress) / 2f)

        // Aligns the bottom with the bottom of the stashed handle.
        val bottom =
            canvas.height - bottomMargin +
                bottomMarginProgress +
                translationYForSwipe +
                translationYForStash +
                -mapRange(
                    1f - progress,
                    0f,
                    if (isAnimatingPinning) 0f else stashedHandleHeight / 2f
                )

        // Draw shadow.
        val newShadowAlpha =
            mapToRange(paint.alpha.toFloat(), 0f, 255f, 0f, shadowAlpha, Interpolators.LINEAR)
        paint.setShadowLayer(
            shadowBlur,
            0f,
            keyShadowDistance,
            setColorAlphaBound(Color.BLACK, Math.round(newShadowAlpha))
        )
        strokePaint.alpha = (paint.alpha * strokeAlpha) / 255

        lastDrawnTransientRect.set(
            transientBackgroundBounds.left + halfWidthDelta,
            bottom - newBackgroundHeight,
            transientBackgroundBounds.right - halfWidthDelta,
            bottom
        )
        val horizontalInset = fullWidth * widthInsetPercentage
        lastDrawnTransientRect.inset(horizontalInset, 0f)

        canvas.drawRoundRect(lastDrawnTransientRect, radius, radius, paint)
        canvas.drawRoundRect(lastDrawnTransientRect, radius, radius, strokePaint)
    }

    /**
     * Sets the width percentage to inset the transient taskbar's background from the left and from
     * the right.
     */
    fun setBackgroundHorizontalInsets(insetPercentage: Float) {
        widthInsetPercentage = insetPercentage
    }

    companion object {
        const val DEFAULT_ROUNDNESS = 1f
        private const val DARK_THEME_STROKE_ALPHA = 51
        private const val LIGHT_THEME_STROKE_ALPHA = 41
        private const val DARK_THEME_SHADOW_ALPHA = 51f
        private const val LIGHT_THEME_SHADOW_ALPHA = 25f
    }
}
