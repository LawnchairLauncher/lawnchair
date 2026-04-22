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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/** Shows a drop target within this view. */
class DropTargetView(context: Context) : View(context) {

    private val rectPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(com.android.internal.R.color.materialColorPrimaryFixed)
            style = Paint.Style.FILL
            alpha = (0.35f * 255).toInt()
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(com.android.internal.R.color.materialColorPrimaryFixed)
            style = Paint.Style.STROKE
            strokeWidth = 2.dpToPx()
        }

    private var cornerRadius = 0f

    private val rect = RectF(0f, 0f, 0f, 0f)

    // TODO b/396539130: Use shared xml resources once we can access them in launcher
    private fun Int.dpToPx() =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        )

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rectPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        canvas.restore()
    }

    fun update(positionRect: RectF, cornerRadius: Float) {
        this.cornerRadius = cornerRadius
        rect.set(positionRect)
        invalidate()
    }

    fun getRect(): RectF {
        return RectF(rect)
    }
}
