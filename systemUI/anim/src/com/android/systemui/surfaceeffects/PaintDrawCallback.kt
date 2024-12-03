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

package com.android.systemui.surfaceeffects

import android.graphics.Paint
import android.graphics.RenderEffect

/**
 * A callback with a [Paint] object that contains shader info, which is triggered every frame while
 * animation is playing. Note that the [Paint] object here is always the same instance.
 *
 * This approach is more performant than other ones because [RenderEffect] forces an intermediate
 * render pass of the View to a texture to feed into it.
 *
 * The usage of this callback is as follows:
 * <pre>{@code
 *     private var paint: Paint? = null
 *     // Override [View.onDraw].
 *     override fun onDraw(canvas: Canvas) {
 *         // RuntimeShader requires hardwareAcceleration.
 *         if (!canvas.isHardwareAccelerated) return
 *
 *         paint?.let { canvas.drawPaint(it) }
 *     }
 *
 *     // Given that this is called [PaintDrawCallback.onDraw]
 *     fun draw(paint: Paint) {
 *         this.paint = paint
 *
 *         // Must call invalidate to trigger View#onDraw
 *         invalidate()
 *     }
 * }</pre>
 *
 * Please refer to [RenderEffectDrawCallback] for alternative approach.
 */
interface PaintDrawCallback {
    fun onDraw(paint: Paint)
}
