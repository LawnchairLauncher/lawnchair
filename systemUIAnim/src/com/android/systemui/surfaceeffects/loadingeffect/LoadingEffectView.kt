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

package com.android.systemui.surfaceeffects.loadingeffect

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Custom View for drawing the [LoadingEffect] with [Canvas.drawPaint]. */
open class LoadingEffectView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var paint: Paint? = null
    private var blendMode: BlendMode = BlendMode.SRC_OVER

    override fun onDraw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            return
        }
        paint?.let { canvas.drawPaint(it) }
    }

    /** Designed to be called on [LoadingEffect.PaintDrawCallback.onDraw]. */
    fun draw(paint: Paint) {
        this.paint = paint
        this.paint!!.blendMode = blendMode

        invalidate()
    }

    /** Sets the blend mode of the [Paint]. */
    fun setBlendMode(blendMode: BlendMode) {
        this.blendMode = blendMode
    }
}
