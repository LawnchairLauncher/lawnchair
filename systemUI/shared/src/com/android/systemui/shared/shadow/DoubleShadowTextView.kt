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

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import com.android.systemui.shared.R
import com.android.systemui.shared.shadow.DoubleShadowTextHelper.ShadowInfo
import com.android.systemui.shared.shadow.DoubleShadowTextHelper.applyShadows

/** Extension of [TextView] which draws two shadows on the text (ambient and key shadows} */
open class DoubleShadowTextView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TextView(context, attrs, defStyleAttr, defStyleRes) {
    private val mKeyShadowInfo: ShadowInfo
    private val mAmbientShadowInfo: ShadowInfo

    init {
        val attributes =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DoubleShadowTextView,
                defStyleAttr,
                defStyleRes
            )
        val drawableSize: Int
        val drawableInsetSize: Int
        try {
            val keyShadowBlur =
                attributes.getDimension(R.styleable.DoubleShadowTextView_keyShadowBlur, 0f)
            val keyShadowOffsetX =
                attributes.getDimension(R.styleable.DoubleShadowTextView_keyShadowOffsetX, 0f)
            val keyShadowOffsetY =
                attributes.getDimension(R.styleable.DoubleShadowTextView_keyShadowOffsetY, 0f)
            val keyShadowAlpha =
                attributes.getFloat(R.styleable.DoubleShadowTextView_keyShadowAlpha, 0f)
            mKeyShadowInfo =
                ShadowInfo(keyShadowBlur, keyShadowOffsetX, keyShadowOffsetY, keyShadowAlpha)
            val ambientShadowBlur =
                attributes.getDimension(R.styleable.DoubleShadowTextView_ambientShadowBlur, 0f)
            val ambientShadowOffsetX =
                attributes.getDimension(R.styleable.DoubleShadowTextView_ambientShadowOffsetX, 0f)
            val ambientShadowOffsetY =
                attributes.getDimension(R.styleable.DoubleShadowTextView_ambientShadowOffsetY, 0f)
            val ambientShadowAlpha =
                attributes.getFloat(R.styleable.DoubleShadowTextView_ambientShadowAlpha, 0f)
            mAmbientShadowInfo =
                ShadowInfo(
                    ambientShadowBlur,
                    ambientShadowOffsetX,
                    ambientShadowOffsetY,
                    ambientShadowAlpha
                )
            drawableSize =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextView_drawableIconSize,
                    0
                )
            drawableInsetSize =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextView_drawableIconInsetSize,
                    0
                )
        } finally {
            attributes.recycle()
        }

        val drawables = arrayOf<Drawable?>(null, null, null, null)
        for ((index, drawable) in compoundDrawablesRelative.withIndex()) {
            if (drawable == null) continue
            drawables[index] =
                DoubleShadowIconDrawable(
                    mKeyShadowInfo,
                    mAmbientShadowInfo,
                    drawable,
                    drawableSize,
                    drawableInsetSize
                )
        }
        setCompoundDrawablesRelative(drawables[0], drawables[1], drawables[2], drawables[3])
    }

    public override fun onDraw(canvas: Canvas) {
        applyShadows(mKeyShadowInfo, mAmbientShadowInfo, this, canvas) { super.onDraw(canvas) }
    }
}
