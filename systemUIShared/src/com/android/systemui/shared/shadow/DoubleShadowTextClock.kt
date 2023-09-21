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
import android.util.AttributeSet
import android.widget.TextClock
import com.android.systemui.shared.R
import com.android.systemui.shared.shadow.DoubleShadowTextHelper.ShadowInfo
import com.android.systemui.shared.shadow.DoubleShadowTextHelper.applyShadows
import kotlin.math.floor

/** Extension of [TextClock] which draws two shadows on the text (ambient and key shadows) */
class DoubleShadowTextClock
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TextClock(context, attrs, defStyleAttr, defStyleRes) {
    private val mAmbientShadowInfo: ShadowInfo
    private val mKeyShadowInfo: ShadowInfo

    init {
        val attributes =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DoubleShadowTextClock,
                defStyleAttr,
                defStyleRes
            )
        try {
            val keyShadowBlur =
                attributes.getDimensionPixelSize(R.styleable.DoubleShadowTextClock_keyShadowBlur, 0)
            val keyShadowOffsetX =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_keyShadowOffsetX,
                    0
                )
            val keyShadowOffsetY =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_keyShadowOffsetY,
                    0
                )
            val keyShadowAlpha =
                attributes.getFloat(R.styleable.DoubleShadowTextClock_keyShadowAlpha, 0f)
            mKeyShadowInfo =
                ShadowInfo(
                    keyShadowBlur.toFloat(),
                    keyShadowOffsetX.toFloat(),
                    keyShadowOffsetY.toFloat(),
                    keyShadowAlpha
                )
            val ambientShadowBlur =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_ambientShadowBlur,
                    0
                )
            val ambientShadowOffsetX =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_ambientShadowOffsetX,
                    0
                )
            val ambientShadowOffsetY =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_ambientShadowOffsetY,
                    0
                )
            val ambientShadowAlpha =
                attributes.getFloat(R.styleable.DoubleShadowTextClock_ambientShadowAlpha, 0f)
            mAmbientShadowInfo =
                ShadowInfo(
                    ambientShadowBlur.toFloat(),
                    ambientShadowOffsetX.toFloat(),
                    ambientShadowOffsetY.toFloat(),
                    ambientShadowAlpha
                )
            val removeTextDescent =
                attributes.getBoolean(R.styleable.DoubleShadowTextClock_removeTextDescent, false)
            val textDescentExtraPadding =
                attributes.getDimensionPixelSize(
                    R.styleable.DoubleShadowTextClock_textDescentExtraPadding,
                    0
                )
            if (removeTextDescent) {
                setPaddingRelative(
                    0,
                    0,
                    0,
                    textDescentExtraPadding - floor(paint.fontMetrics.descent.toDouble()).toInt()
                )
            }
        } finally {
            attributes.recycle()
        }
    }

    public override fun onDraw(canvas: Canvas) {
        applyShadows(mKeyShadowInfo, mAmbientShadowInfo, this, canvas) { super.onDraw(canvas) }
    }
}
