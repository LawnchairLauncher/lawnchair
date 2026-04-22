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

package com.android.launcher3.views

import android.content.res.ColorStateList
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
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting

/**
 * Launcher wrapper for Drawables to provide a double shadow effect. Currently for use with
 * [DoubleShadowBubbleTextView] to provide a similar shadow to inline icons.
 */
@RequiresApi(VERSION_CODES.S)
class DoubleShadowIconDrawable(
    private val shadowInfo: ShadowInfo,
    iconDrawable: Drawable,
    private val iconSize: Int,
    iconInsetSize: Int
) : Drawable() {
    private val mIconDrawable: InsetDrawable
    private val mDoubleShadowNode: RenderNode?

    init {
        mIconDrawable = InsetDrawable(iconDrawable, iconInsetSize)
        mIconDrawable.setBounds(0, 0, iconSize, iconSize)
        mDoubleShadowNode = createShadowRenderNode()
    }

    @VisibleForTesting
    fun createShadowRenderNode(): RenderNode {
        val renderNode = RenderNode("DoubleShadowNode")
        renderNode.setPosition(0, 0, iconSize, iconSize)
        // Create render effects
        val ambientShadow =
            createShadowRenderEffect(
                shadowInfo.ambientShadowBlur,
                0f,
                0f,
                Color.alpha(shadowInfo.ambientShadowColor).toFloat()
            )
        val keyShadow =
            createShadowRenderEffect(
                shadowInfo.keyShadowBlur,
                shadowInfo.keyShadowOffsetX,
                shadowInfo.keyShadowOffsetY,
                Color.alpha(shadowInfo.keyShadowColor).toFloat()
            )
        val blend = RenderEffect.createBlendModeEffect(ambientShadow, keyShadow, BlendMode.DST_ATOP)
        renderNode.setRenderEffect(blend)
        return renderNode
    }

    @VisibleForTesting
    fun createShadowRenderEffect(
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
        if (canvas.isHardwareAccelerated && mDoubleShadowNode != null) {
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

    override fun getIntrinsicHeight() = iconSize

    override fun getIntrinsicWidth() = iconSize

    override fun getOpacity() = PixelFormat.TRANSPARENT

    override fun setAlpha(alpha: Int) {
        mIconDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mIconDrawable.colorFilter = colorFilter
    }

    override fun setTint(color: Int) {
        mIconDrawable.setTint(color)
    }

    override fun setTintList(tint: ColorStateList?) {
        mIconDrawable.setTintList(tint)
    }
}
