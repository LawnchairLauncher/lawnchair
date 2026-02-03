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

package com.android.quickstep.task.thumbnail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object SplashHelper {
    private val BITMAP_RECT_COLORS = listOf(Color.GREEN, Color.RED, Color.BLUE, Color.CYAN)

    fun createSplash(): Bitmap = createBitmap(width = 20, height = 20, rectColorRotation = 1)

    fun createBitmap(width: Int, height: Int, rectColorRotation: Int = 0): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                val paint = Paint()
                paint.color = BITMAP_RECT_COLORS[rectColorRotation % 4]
                drawRect(0f, 0f, width / 2f, height / 2f, paint)
                paint.color = BITMAP_RECT_COLORS[(1 + rectColorRotation) % 4]
                drawRect(width / 2f, 0f, width.toFloat(), height / 2f, paint)
                paint.color = BITMAP_RECT_COLORS[(2 + rectColorRotation) % 4]
                drawRect(0f, height / 2f, width / 2f, height.toFloat(), paint)
                paint.color = BITMAP_RECT_COLORS[(3 + rectColorRotation) % 4]
                drawRect(width / 2f, height / 2f, width.toFloat(), height.toFloat(), paint)
            }
        }
}
