/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.shared.bubbles

import android.annotation.ColorInt
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.properties.Delegates

/** A drawable for the [BubblePopupView] that draws a popup background with a directional arrow */
class BubblePopupDrawable(val config: Config) : Drawable() {
    /** The direction of the arrow in the popup drawable */
    enum class ArrowDirection {
        UP,
        DOWN
    }

    /** The arrow position on the side of the popup bubble */
    sealed class ArrowPosition {
        object Start : ArrowPosition()
        object Center : ArrowPosition()
        object End : ArrowPosition()
        class Custom(val value: Float) : ArrowPosition()
    }

    /** The configuration for drawable features */
    data class Config(
        @ColorInt val color: Int,
        val cornerRadius: Float,
        val contentPadding: Int,
        val arrowWidth: Float,
        val arrowHeight: Float,
        val arrowRadius: Float
    )

    /**
     * The direction of the arrow in the popup drawable. It affects the content padding and requires
     * it to be updated in the view.
     */
    var arrowDirection: ArrowDirection by
        Delegates.observable(ArrowDirection.UP) { _, _, _ -> requestPathUpdate() }

    /**
     * Arrow position along the X axis and its direction. The position is adjusted to the content
     * corner radius when applied so it doesn't go into rounded corner area
     */
    var arrowPosition: ArrowPosition by
        Delegates.observable(ArrowPosition.Center) { _, _, _ -> requestPathUpdate() }

    private val path = Path()
    private val paint = Paint()
    private var shouldUpdatePath = true

    init {
        paint.color = config.color
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        updatePathIfNeeded()
        canvas.drawPath(path, paint)
    }

    override fun onBoundsChange(bounds: Rect) {
        requestPathUpdate()
    }

    /** Should be applied to the view padding if arrow direction changes */
    override fun getPadding(padding: Rect): Boolean {
        padding.set(
            config.contentPadding,
            config.contentPadding,
            config.contentPadding,
            config.contentPadding
        )
        when (arrowDirection) {
            ArrowDirection.UP -> padding.top += config.arrowHeight.toInt()
            ArrowDirection.DOWN -> padding.bottom += config.arrowHeight.toInt()
        }
        return true
    }

    override fun getOutline(outline: Outline) {
        updatePathIfNeeded()
        outline.setPath(path)
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

    /** Schedules path update for the next redraw */
    private fun requestPathUpdate() {
        shouldUpdatePath = true
    }

    /** Updates the path if required, when bounds or arrow direction/position changes */
    private fun updatePathIfNeeded() {
        if (shouldUpdatePath) {
            updatePath()
            shouldUpdatePath = false
        }
    }

    /** Updates the path value using the current bounds, config, arrow direction and position */
    private fun updatePath() {
        if (bounds.isEmpty) return
        // Reset the path state
        path.reset()
        // The content rect where the filled rounded rect will be drawn
        val contentRect = RectF(bounds)
        when (arrowDirection) {
            ArrowDirection.UP -> {
                // Add rounded arrow pointing up to the path
                addRoundedArrowPositioned(path, arrowPosition)
                // Inset content rect by the arrow size from the top
                contentRect.top += config.arrowHeight
            }
            ArrowDirection.DOWN -> {
                val matrix = Matrix()
                // Flip the path with the matrix to draw arrow pointing down
                matrix.setScale(1f, -1f, bounds.width() / 2f, bounds.height() / 2f)
                path.transform(matrix)
                // Add rounded arrow with the flipped matrix applied, will point down
                addRoundedArrowPositioned(path, arrowPosition)
                // Restore the path matrix to the original state with inverted matrix
                matrix.invert(matrix)
                path.transform(matrix)
                // Inset content rect by the arrow size from the bottom
                contentRect.bottom -= config.arrowHeight
            }
        }
        // Add the content area rounded rect
        path.addRoundRect(contentRect, config.cornerRadius, config.cornerRadius, Path.Direction.CW)
    }

    /** Add a rounded arrow pointing up in the horizontal position on the canvas */
    private fun addRoundedArrowPositioned(path: Path, position: ArrowPosition) {
        val matrix = Matrix()
        var translationX = positionValue(position) - config.arrowWidth / 2
        // Offset to position between rounded corners of the content view
        translationX = translationX.coerceIn(config.cornerRadius,
                bounds.width() - config.cornerRadius - config.arrowWidth)
        // Translate to add the arrow in the center horizontally
        matrix.setTranslate(-translationX, 0f)
        path.transform(matrix)
        // Add rounded arrow
        addRoundedArrow(path)
        // Restore the path matrix to the original state with inverted matrix
        matrix.invert(matrix)
        path.transform(matrix)
    }

    /** Adds a rounded arrow pointing up to the path, can be flipped if needed */
    private fun addRoundedArrow(path: Path) {
        // Theta is half of the angle inside the triangle tip
        val thetaTan = config.arrowWidth / (config.arrowHeight * 2f)
        val theta = atan(thetaTan)
        val thetaDeg = Math.toDegrees(theta.toDouble()).toFloat()
        // The center Y value of the circle for the triangle tip
        val tipCircleCenterY = config.arrowRadius / sin(theta)
        // The length from triangle tip to intersection point with the circle
        val tipIntersectionSideLength = config.arrowRadius / thetaTan
        // The offset from the top to the point of intersection
        val intersectionTopOffset = tipIntersectionSideLength * cos(theta)
        // The offset from the center to the point of intersection
        val intersectionCenterOffset = tipIntersectionSideLength * sin(theta)
        // The center X of the triangle
        val arrowCenterX = config.arrowWidth / 2f

        // Set initial position in bottom left of the arrow
        path.moveTo(0f, config.arrowHeight)
        // Add the left side of the triangle
        path.lineTo(arrowCenterX - intersectionCenterOffset, intersectionTopOffset)
        // Add the arc from the left to the right side of the triangle
        path.arcTo(
            /* left = */ arrowCenterX - config.arrowRadius,
            /* top = */ tipCircleCenterY - config.arrowRadius,
            /* right = */ arrowCenterX + config.arrowRadius,
            /* bottom = */ tipCircleCenterY + config.arrowRadius,
            /* startAngle = */ 180 + thetaDeg,
            /* sweepAngle = */ 180 - (2 * thetaDeg),
            /* forceMoveTo = */ false
        )
        // Add the right side of the triangle
        path.lineTo(config.arrowWidth, config.arrowHeight)
        // Close the path
        path.close()
    }

    /** The value of the arrow position provided the position and current bounds */
    private fun positionValue(position: ArrowPosition): Float {
        return when (position) {
            is ArrowPosition.Start -> 0f
            is ArrowPosition.Center -> bounds.width().toFloat() / 2f
            is ArrowPosition.End -> bounds.width().toFloat()
            is ArrowPosition.Custom -> position.value
        }
    }
}
