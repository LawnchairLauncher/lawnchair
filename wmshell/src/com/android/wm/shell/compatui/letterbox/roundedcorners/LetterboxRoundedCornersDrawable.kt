/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.android.wm.shell.common.ShellExecutor

/** Rounded corner [Drawable] implementation */
class LetterboxRoundedCornersDrawable(
    private var cornerColor: Color,
    private var radius: Float = 0f,
) : Drawable() {

    enum class FlipType {
        FLIP_VERTICAL,
        FLIP_HORIZONTAL,
    }

    companion object {
        @JvmStatic private val ANIMATION_DURATION = 350L

        // To make the animation visible we add a small delay
        @JvmStatic private val ANIMATION_DELAY = 200L
    }

    private val currentBounds = RectF()
    private var verticalFlipped = false
    private var horizontalFlipped = false

    private var currentRadius = 0f

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cornerColor.toArgb()
            style = Paint.Style.FILL
        }

    private val trPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

    private val squarePath = Path()
    private val circlePath = Path()
    private val path = Path()

    val radii =
        floatArrayOf(
            0f,
            0f, // Top-left corner
            0f,
            0f, // Top-right corner
            0f,
            0f, // Bottom-right corner
            0f,
            0f, // Bottom-left corner
        )

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    fun setCornerColor(newColor: Int) {
        path.reset()
        paint.color = newColor
        onBoundsChange(currentBounds.toRect())
        invalidateSelf() // Trigger redraw
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        squarePath.reset()
        path.reset()
        currentBounds.set(bounds)
        squarePath.addRect(currentBounds, Path.Direction.CW)
        updatePath(currentRadius)
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        trPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        trPaint.colorFilter = colorFilter
    }

    @kotlin.Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    fun show(executor: ShellExecutor, immediate: Boolean = false) {
        if (immediate) {
            currentRadius = radius
            updatePath(currentRadius)
            invalidateSelf()
            return
        }
        animateRadius(executor, currentRadius, radius)
    }

    fun hide(executor: ShellExecutor, immediate: Boolean = false) {
        if (immediate) {
            currentRadius = 0f
            updatePath(currentRadius)
            invalidateSelf()
            return
        }
        animateRadius(executor, currentRadius, 0f)
    }

    @SuppressLint("Recycle")
    private fun animateRadius(executor: ShellExecutor, fromRadius: Float, targetRadius: Float) {
        ValueAnimator.ofFloat(fromRadius, targetRadius).apply {
            this.duration = ANIMATION_DURATION
            addListener(
                object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        currentRadius = targetRadius
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        currentRadius = fromRadius
                    }

                    override fun onAnimationRepeat(animation: Animator) {}
                }
            )
            addUpdateListener { animation ->
                updatePath(animation.animatedValue as Float) // Update the path with the new radius
                invalidateSelf() // Trigger redraw
            }
            //  This is where start is invoked.
            executor.executeDelayed(::start, ANIMATION_DELAY)
        }
    }

    private fun updatePath(radius: Float) {
        path.reset()
        circlePath.reset()
        radii[0] = radius
        radii[1] = radius
        circlePath.addRoundRect(currentBounds, radii, Path.Direction.CCW)
        path.op(squarePath, circlePath, Path.Op.DIFFERENCE)
        path.flip()
    }

    private fun RectF.toRect() =
        Rect(this.top.toInt(), this.left.toInt(), this.right.toInt(), this.bottom.toInt())

    fun flip(flipType: FlipType): LetterboxRoundedCornersDrawable {
        when (flipType) {
            FlipType.FLIP_VERTICAL -> verticalFlipped = !verticalFlipped
            FlipType.FLIP_HORIZONTAL -> horizontalFlipped = !horizontalFlipped
        }
        return this
    }

    private fun Path.flip() {
        val matrix = Matrix()
        if (horizontalFlipped) {
            matrix.preScale(
                -1f,
                1f,
                bounds.centerX().toFloat(),
                bounds.centerY().toFloat(),
            ) // Flip horizontally
        }
        if (verticalFlipped) {
            matrix.preScale(
                1f,
                -1f,
                bounds.centerX().toFloat(),
                bounds.centerY().toFloat(),
            ) // Flip vertically
        }
        transform(matrix)
    }
}
