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

package com.android.launcher3.widgetpicker.ui.components

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * An [AppWidgetHostView] that renders the widget content for previewing in the widget picker.
 *
 * The contents of preview cannot be interacted with.
 */
class WidgetPreviewHostView(context: Context) : AppWidgetHostView(context) {
    private var previewContainerSizePx: IntSize? = null
    private var contentScale = 1f

    init {
        clipToPadding = false
        clipChildren = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true // disable interactions with the preview.
    }

    /**
     * Sets the size of the app widget host view, so that widget can constraint the contents to the
     * given size.
     */
    fun setContainerSizePx(sizePx: IntSize) {
        previewContainerSizePx = sizePx
    }

    // Prevent default padding being set on the view based on provider info; we manage our own
    // widget spacing
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {}

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        previewContainerSizePx?.let { containerSizePx ->
            val child = getChildAt(0)
            val (childWidth, childHeight) = measureChild(child, containerSizePx)

            val widthScale: Float = containerSizePx.width / childWidth
            val heightScale: Float = containerSizePx.height / childHeight
            val scale = widthScale.coerceAtMost(heightScale)

            child.scaleX = scale
            child.scaleY = scale
            contentScale = scale

            setMeasuredDimension(
                (scale * childWidth).roundToInt(),
                (scale * childHeight).roundToInt(),
            )
        } ?: super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun measureChild(child: View, containerSizePx: IntSize): Pair<Float, Float> {
        val lp = child.layoutParams as MarginLayoutParams

        val widgetSpec =
            when (lp.width) {
                LayoutParams.MATCH_PARENT ->
                    MeasureSpec.makeMeasureSpec(containerSizePx.width, MeasureSpec.EXACTLY)

                LayoutParams.WRAP_CONTENT ->
                    MeasureSpec.makeMeasureSpec(containerSizePx.width, MeasureSpec.AT_MOST)

                else -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
            }

        val heightSpec =
            when (lp.height) {
                LayoutParams.MATCH_PARENT ->
                    MeasureSpec.makeMeasureSpec(containerSizePx.height, MeasureSpec.EXACTLY)

                LayoutParams.WRAP_CONTENT ->
                    MeasureSpec.makeMeasureSpec(containerSizePx.height, MeasureSpec.AT_MOST)

                else -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
            }

        measureChild(child, widgetSpec, heightSpec)
        return Pair(child.measuredWidth.toFloat(), child.measuredHeight.toFloat())
    }

    /**
     * Returns visual bounds of this preview offset by the provided [offset] and considering the
     * scale of preview.
     */
    fun getDragBoundsForOffset(offset: Offset): Rect {
        val width: Int = (measuredWidth)
        val height: Int = (measuredHeight)
        val bounds = Rect(0, 0, width, height)

        val xOffset: Int =
            left - (offset.x * contentScale).toInt()
        val yOffset: Int =
            top - (offset.y * contentScale).toInt()
        bounds.offset(xOffset, yOffset)

        return bounds
    }
}
