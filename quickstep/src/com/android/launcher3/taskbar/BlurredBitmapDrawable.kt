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
package com.android.launcher3.taskbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.DrawableWrapper

/* BitmapDrawable that can blur the given bitmap. */
class BlurredBitmapDrawable(bitmap: Bitmap?, radiusX: Float, radiusY: Float) :
    DrawableWrapper(BitmapDrawable(bitmap)) {
    private val mBlurRenderNode: RenderNode = RenderNode("BlurredConstraintLayoutBlurNode")

    constructor(bitmap: Bitmap?, radius: Float) : this(bitmap, radius, radius)

    init {
        mBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(radiusX, radiusY, Shader.TileMode.CLAMP)
        )
    }

    override fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            super.draw(canvas)
            return
        }
        mBlurRenderNode.setPosition(bounds)
        if (!mBlurRenderNode.hasDisplayList()) {
            // Record render node if its display list is not recorded or discarded
            // (which happens when it's no longer drawn by anything).
            val recordingCanvas = mBlurRenderNode.beginRecording()
            super.draw(recordingCanvas)
            mBlurRenderNode.endRecording()
        }
        canvas.drawRenderNode(mBlurRenderNode)
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }
}
