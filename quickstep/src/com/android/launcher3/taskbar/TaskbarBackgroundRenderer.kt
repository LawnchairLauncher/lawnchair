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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.android.launcher3.R

/**
 * Helps draw the taskbar background, made up of a rectangle plus two inverted rounded corners.
 */
class TaskbarBackgroundRenderer(context: TaskbarActivityContext) {

    val paint: Paint = Paint()
    var backgroundHeight = context.deviceProfile.taskbarSize.toFloat()

    private val leftCornerRadius = context.leftCornerRadius.toFloat()
    private val rightCornerRadius = context.rightCornerRadius.toFloat()
    private val invertedLeftCornerPath: Path = Path()
    private val invertedRightCornerPath: Path = Path()

    init {
        paint.color = context.getColor(R.color.taskbar_background)
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.FILL

        // Create the paths for the inverted rounded corners above the taskbar. Start with a filled
        // square, and then subtract out a circle from the appropriate corner.
        val square = Path()
        square.addRect(0f, 0f, leftCornerRadius, leftCornerRadius, Path.Direction.CW)
        val circle = Path()
        circle.addCircle(leftCornerRadius, 0f, leftCornerRadius, Path.Direction.CW)
        invertedLeftCornerPath.op(square, circle, Path.Op.DIFFERENCE)
        square.reset()
        square.addRect(0f, 0f, rightCornerRadius, rightCornerRadius, Path.Direction.CW)
        circle.reset()
        circle.addCircle(0f, 0f, rightCornerRadius, Path.Direction.CW)
        invertedRightCornerPath.op(square, circle, Path.Op.DIFFERENCE)
    }

    /**
     * Draws the background with the given paint and height, on the provided canvas.
     */
    fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, canvas.height - backgroundHeight)

        // Draw the background behind taskbar content.
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), backgroundHeight, paint)

        // Draw the inverted rounded corners above the taskbar.
        canvas.translate(0f, -leftCornerRadius)
        canvas.drawPath(invertedLeftCornerPath, paint)
        canvas.translate(0f, leftCornerRadius)
        canvas.translate(canvas.width - rightCornerRadius, -rightCornerRadius)
        canvas.drawPath(invertedRightCornerPath, paint)

        canvas.restore()
    }
}
