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
package com.android.launcher3.taskbar.bubbles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.mapToRange
import com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound
import com.android.launcher3.popup.RoundedArrowDrawable

/** Drawable for the background of the bubble bar. */
class BubbleBarBackground(context: Context, private var backgroundHeight: Float) : Drawable() {

    private val DARK_THEME_SHADOW_ALPHA = 51f
    private val LIGHT_THEME_SHADOW_ALPHA = 25f

    private val paint: Paint = Paint()
    private val pointerWidth: Float
    private val pointerHeight: Float
    private val pointerTipRadius: Float
    private val pointerVisibleHeight: Float

    private val shadowAlpha: Float
    private var shadowBlur = 0f
    private var keyShadowDistance = 0f

    var arrowPositionX: Float = 0f
        private set

    private var showingArrow: Boolean = false
    private var arrowDrawable: RoundedArrowDrawable

    var width: Float = 0f

    /**
     * Set whether the drawable is anchored to the left or right edge of the container.
     *
     * When `anchorLeft` is set to `true`, drawable left edge aligns up with the container left
     * edge. Drawable can be drawn outside container bounds on the right edge. When it is set to
     * `false` (the default), drawable right edge aligns up with the container right edge. Drawable
     * can be drawn outside container bounds on the left edge.
     */
    var anchorLeft: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    init {
        paint.color = context.getColor(R.color.taskbar_background)
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.FILL

        val res = context.resources
        shadowBlur = res.getDimension(R.dimen.transient_taskbar_shadow_blur)
        keyShadowDistance = res.getDimension(R.dimen.transient_taskbar_key_shadow_distance)
        pointerWidth = res.getDimension(R.dimen.bubblebar_pointer_width)
        pointerHeight = res.getDimension(R.dimen.bubblebar_pointer_height)
        pointerVisibleHeight = res.getDimension(R.dimen.bubblebar_pointer_visible_size)
        pointerTipRadius = res.getDimension(R.dimen.bubblebar_pointer_radius)

        shadowAlpha =
            if (Utilities.isDarkTheme(context)) {
                DARK_THEME_SHADOW_ALPHA
            } else {
                LIGHT_THEME_SHADOW_ALPHA
            }

        arrowDrawable =
            RoundedArrowDrawable.createVerticalRoundedArrow(
                pointerWidth,
                pointerHeight,
                pointerTipRadius,
                /* isPointingUp= */ true,
                context.getColor(R.color.taskbar_background)
            )
        arrowDrawable.setBounds(0, 0, pointerWidth.toInt(), pointerHeight.toInt())
    }

    fun showArrow(show: Boolean) {
        showingArrow = show
    }

    fun setArrowPosition(x: Float) {
        arrowPositionX = x
    }

    /** Draws the background with the given paint and height, on the provided canvas. */
    override fun draw(canvas: Canvas) {
        canvas.save()

        // TODO (b/277359345): Should animate the alpha similar to taskbar (see TaskbarDragLayer)
        // Draw shadows.
        val newShadowAlpha =
            mapToRange(paint.alpha.toFloat(), 0f, 255f, 0f, shadowAlpha, Interpolators.LINEAR)
        paint.setShadowLayer(
            shadowBlur,
            0f,
            keyShadowDistance,
            setColorAlphaBound(Color.BLACK, Math.round(newShadowAlpha))
        )
        arrowDrawable.setShadowLayer(
            shadowBlur,
            0f,
            keyShadowDistance,
            setColorAlphaBound(Color.BLACK, Math.round(newShadowAlpha))
        )

        // Draw background.
        val radius = backgroundHeight / 2f
        val left = bounds.left + (if (anchorLeft) 0f else bounds.width().toFloat() - width)
        val right = bounds.left + (if (anchorLeft) width else bounds.width().toFloat())
        val top = bounds.top + pointerVisibleHeight
        val bottom = bounds.top + bounds.height().toFloat()
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)

        if (showingArrow) {
            // Draw arrow.
            val transX = bounds.left + arrowPositionX - pointerWidth / 2f
            canvas.translate(transX, 0f)
            arrowDrawable.draw(canvas)
        }

        canvas.restore()
    }

    override fun getOpacity(): Int {
        return when (paint.alpha) {
            255 -> PixelFormat.OPAQUE
            0 -> PixelFormat.TRANSPARENT
            else -> PixelFormat.TRANSLUCENT
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        arrowDrawable.alpha = alpha
    }

    override fun getAlpha(): Int {
        return paint.alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    fun setArrowAlpha(alpha: Int) {
        arrowDrawable.alpha = alpha
    }

    fun setHeight(newHeight: Float) {
        backgroundHeight = newHeight
    }
}
