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
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.mapRange
import com.android.launcher3.Utilities.mapToRange
import com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound
import com.android.launcher3.util.DisplayController

/** Helps draw the taskbar background, made up of a rectangle plus two inverted rounded corners. */
class TaskbarBackgroundRenderer(context: TaskbarActivityContext) {

    private val isInSetup: Boolean = !context.isUserSetupComplete
    private val DARK_THEME_SHADOW_ALPHA = 51f
    private val LIGHT_THEME_SHADOW_ALPHA = 25f

    val paint = Paint()
    val lastDrawnTransientRect = RectF()
    var backgroundHeight = context.deviceProfile.taskbarHeight.toFloat()
    var translationYForSwipe = 0f
    var translationYForStash = 0f

    private var maxBackgroundHeight = context.deviceProfile.taskbarHeight.toFloat()
    private val transientBackgroundBounds = context.transientTaskbarBounds

    private val isTransientTaskbar = DisplayController.isTransientTaskbar(context)

    private val shadowAlpha: Float
    private var shadowBlur = 0f
    private var keyShadowDistance = 0f
    private var bottomMargin = 0

    private val fullLeftCornerRadius = context.leftCornerRadius.toFloat()
    private val fullRightCornerRadius = context.rightCornerRadius.toFloat()
    private var leftCornerRadius = fullLeftCornerRadius
    private var rightCornerRadius = fullRightCornerRadius
    private var widthInsetPercentage = 0f
    private val square: Path = Path()
    private val circle: Path = Path()
    private val invertedLeftCornerPath: Path = Path()
    private val invertedRightCornerPath: Path = Path()

    private var stashedHandleWidth =
        context.resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_width)

    private val stashedHandleHeight =
        context.resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_height)

    init {
        paint.color = context.getColor(R.color.taskbar_background)
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.FILL

        if (isTransientTaskbar) {
            val res = context.resources
            bottomMargin = res.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin)
            shadowBlur = res.getDimension(R.dimen.transient_taskbar_shadow_blur)
            keyShadowDistance = res.getDimension(R.dimen.transient_taskbar_key_shadow_distance)
        }

        shadowAlpha =
            if (Utilities.isDarkTheme(context)) DARK_THEME_SHADOW_ALPHA
            else LIGHT_THEME_SHADOW_ALPHA

        setCornerRoundness(DEFAULT_ROUNDNESS)
    }

    fun updateStashedHandleWidth(dp: DeviceProfile, res: Resources) {
        stashedHandleWidth = res.getDimensionPixelSize(
                if (TaskbarManager.isPhoneMode(dp)) R.dimen.taskbar_stashed_small_screen
                else R.dimen.taskbar_stashed_handle_width
        )
    }

    /**
     * Sets the roundness of the round corner above Taskbar. No effect on transient Taskkbar.
     *
     * @param cornerRoundness 0 has no round corner, 1 has complete round corner.
     */
    fun setCornerRoundness(cornerRoundness: Float) {
        if (isTransientTaskbar && !transientBackgroundBounds.isEmpty) {
            return
        }

        leftCornerRadius = fullLeftCornerRadius * cornerRoundness
        rightCornerRadius = fullRightCornerRadius * cornerRoundness

        // Create the paths for the inverted rounded corners above the taskbar. Start with a filled
        // square, and then subtract out a circle from the appropriate corner.
        square.reset()
        square.addRect(0f, 0f, leftCornerRadius, leftCornerRadius, Path.Direction.CW)
        circle.reset()
        circle.addCircle(leftCornerRadius, 0f, leftCornerRadius, Path.Direction.CW)
        invertedLeftCornerPath.op(square, circle, Path.Op.DIFFERENCE)

        square.reset()
        square.addRect(0f, 0f, rightCornerRadius, rightCornerRadius, Path.Direction.CW)
        circle.reset()
        circle.addCircle(0f, 0f, rightCornerRadius, Path.Direction.CW)
        invertedRightCornerPath.op(square, circle, Path.Op.DIFFERENCE)
    }

    /** Draws the background with the given paint and height, on the provided canvas. */
    fun draw(canvas: Canvas) {
        canvas.save()
        if (!isTransientTaskbar || transientBackgroundBounds.isEmpty) {
            canvas.translate(0f, canvas.height - backgroundHeight - bottomMargin)
            // Draw the background behind taskbar content.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), backgroundHeight, paint)

            // Draw the inverted rounded corners above the taskbar.
            canvas.translate(0f, -leftCornerRadius)
            canvas.drawPath(invertedLeftCornerPath, paint)
            canvas.translate(0f, leftCornerRadius)
            canvas.translate(canvas.width - rightCornerRadius, -rightCornerRadius)
            canvas.drawPath(invertedRightCornerPath, paint)
        } else if (!isInSetup) {
            // backgroundHeight is a value from [0...maxBackgroundHeight], so we can use it as a
            // proxy to figure out the animation progress of the stash/unstash animation.
            val progress = backgroundHeight / maxBackgroundHeight

            // At progress 0, we draw the background as the stashed handle.
            // At progress 1, we draw the background as the full taskbar.
            val newBackgroundHeight =
                mapRange(progress, stashedHandleHeight.toFloat(), maxBackgroundHeight)
            val fullWidth = transientBackgroundBounds.width()
            val newWidth = mapRange(progress, stashedHandleWidth.toFloat(), fullWidth.toFloat())
            val halfWidthDelta = (fullWidth - newWidth) / 2f
            val radius = newBackgroundHeight / 2f
            val bottomMarginProgress = bottomMargin * ((1f - progress) / 2f)

            // Aligns the bottom with the bottom of the stashed handle.
            val bottom =
                canvas.height - bottomMargin +
                    bottomMarginProgress +
                    translationYForSwipe +
                    translationYForStash +
                    -mapRange(1f - progress, 0f, stashedHandleHeight / 2f)

            // Draw shadow.
            val newShadowAlpha =
                mapToRange(paint.alpha.toFloat(), 0f, 255f, 0f, shadowAlpha, Interpolators.LINEAR)
            paint.setShadowLayer(
                shadowBlur,
                0f,
                keyShadowDistance,
                setColorAlphaBound(Color.BLACK, Math.round(newShadowAlpha))
            )

            lastDrawnTransientRect.set(
                transientBackgroundBounds.left + halfWidthDelta,
                bottom - newBackgroundHeight,
                transientBackgroundBounds.right - halfWidthDelta,
                bottom
            )
            val horizontalInset = fullWidth * widthInsetPercentage
            lastDrawnTransientRect.inset(horizontalInset, 0f)

            canvas.drawRoundRect(lastDrawnTransientRect, radius, radius, paint)
        }
        canvas.restore()
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
    }
}
