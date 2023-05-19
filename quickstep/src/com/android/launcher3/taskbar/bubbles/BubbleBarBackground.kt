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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.mapToRange
import com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.wm.shell.common.TriangleShape

/** Drawable for the background of the bubble bar. */
class BubbleBarBackground(context: TaskbarActivityContext, private val backgroundHeight: Float) :
    Drawable() {

    private val DARK_THEME_SHADOW_ALPHA = 51f
    private val LIGHT_THEME_SHADOW_ALPHA = 25f

    private val paint: Paint = Paint()
    private val pointerSize: Float

    private val shadowAlpha: Float
    private var shadowBlur = 0f
    private var keyShadowDistance = 0f

    var arrowPositionX: Float = 0f
        private set
    private var showingArrow: Boolean = false
    private var arrowDrawable: ShapeDrawable

    var width: Float = 0f

    init {
        paint.color = context.getColor(R.color.taskbar_background)
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.FILL

        val res = context.resources
        shadowBlur = res.getDimension(R.dimen.transient_taskbar_shadow_blur)
        keyShadowDistance = res.getDimension(R.dimen.transient_taskbar_key_shadow_distance)
        pointerSize = res.getDimension(R.dimen.bubblebar_pointer_size)

        shadowAlpha =
            if (Utilities.isDarkTheme(context)) {
                DARK_THEME_SHADOW_ALPHA
            } else {
                LIGHT_THEME_SHADOW_ALPHA
            }

        arrowDrawable =
            ShapeDrawable(TriangleShape.create(pointerSize, pointerSize, /* pointUp= */ true))
        arrowDrawable.setBounds(0, 0, pointerSize.toInt(), pointerSize.toInt())
        arrowDrawable.paint.flags = Paint.ANTI_ALIAS_FLAG
        arrowDrawable.paint.style = Paint.Style.FILL
        arrowDrawable.paint.color = context.getColor(R.color.taskbar_background)
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
        arrowDrawable.paint.setShadowLayer(
            shadowBlur,
            0f,
            keyShadowDistance,
            setColorAlphaBound(Color.BLACK, Math.round(newShadowAlpha))
        )

        // Draw background.
        val radius = backgroundHeight / 2f
        canvas.drawRoundRect(
            canvas.width.toFloat() - width,
            0f,
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            radius,
            radius,
            paint
        )

        if (showingArrow) {
            // Draw arrow.
            val transX = arrowPositionX - pointerSize / 2f
            canvas.translate(transX, -pointerSize)
            arrowDrawable.draw(canvas)
        }

        canvas.restore()
    }

    override fun getOpacity(): Int {
        return paint.alpha
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    fun setArrowAlpha(alpha: Int) {
        arrowDrawable.paint.alpha = alpha
    }
}
