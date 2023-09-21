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
package com.android.systemui.shared.shadow

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import com.android.systemui.shared.shadow.DoubleShadowTextHelper.ShadowInfo

/** A component to draw an icon with two layers of shadows. */
class DoubleShadowIconDrawable(
    keyShadowInfo: ShadowInfo,
    ambientShadowInfo: ShadowInfo,
    iconDrawable: Drawable,
    iconSize: Int,
    val iconInsetSize: Int
) : Drawable() {
    private val mAmbientShadowInfo: ShadowInfo
    private val mCanvasSize: Int
    private val mKeyShadowInfo: ShadowInfo
    private val mIconDrawable: InsetDrawable
    private val mDoubleShadowNode: RenderNode

    init {
        mCanvasSize = iconSize + iconInsetSize * 2
        mKeyShadowInfo = keyShadowInfo
        mAmbientShadowInfo = ambientShadowInfo
        setBounds(0, 0, mCanvasSize, mCanvasSize)
        mIconDrawable = InsetDrawable(iconDrawable, iconInsetSize)
        mIconDrawable.setBounds(0, 0, mCanvasSize, mCanvasSize)
        mDoubleShadowNode = createShadowRenderNode()
    }

    private fun createShadowRenderNode(): RenderNode {
        val renderNode = RenderNode("DoubleShadowNode")
        renderNode.setPosition(0, 0, mCanvasSize, mCanvasSize)
        // Create render effects
        val ambientShadow =
            createShadowRenderEffect(
                mAmbientShadowInfo.blur,
                mAmbientShadowInfo.offsetX,
                mAmbientShadowInfo.offsetY,
                mAmbientShadowInfo.alpha
            )
        val keyShadow =
            createShadowRenderEffect(
                mKeyShadowInfo.blur,
                mKeyShadowInfo.offsetX,
                mKeyShadowInfo.offsetY,
                mKeyShadowInfo.alpha
            )
        val blend = RenderEffect.createBlendModeEffect(ambientShadow, keyShadow, BlendMode.DST_ATOP)
        renderNode.setRenderEffect(blend)
        return renderNode
    }

    private fun createShadowRenderEffect(
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        alpha: Float
    ): RenderEffect {
        return RenderEffect.createColorFilterEffect(
            PorterDuffColorFilter(Color.argb(alpha, 0f, 0f, 0f), PorterDuff.Mode.MULTIPLY),
            RenderEffect.createOffsetEffect(
                offsetX,
                offsetY,
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        )
    }

    override fun draw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated) {
            if (!mDoubleShadowNode.hasDisplayList()) {
                // Record render node if its display list is not recorded or discarded
                // (which happens when it's no longer drawn by anything).
                val recordingCanvas = mDoubleShadowNode.beginRecording()
                mIconDrawable.draw(recordingCanvas)
                mDoubleShadowNode.endRecording()
            }
            canvas.drawRenderNode(mDoubleShadowNode)
        }
        mIconDrawable.draw(canvas)
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun setAlpha(alpha: Int) {
        mIconDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mIconDrawable.colorFilter = colorFilter
    }

    override fun setTint(color: Int) {
        mIconDrawable.setTint(color)
    }
}
