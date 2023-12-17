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
import android.content.res.Resources
import android.content.res.TypedArray
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
    defStyleRes: Int = 0,
) : TextClock(context, attrs, defStyleAttr, defStyleRes) {
    private lateinit var mAmbientShadowInfo: ShadowInfo
    private lateinit var mKeyShadowInfo: ShadowInfo
    private var attributesInput: TypedArray? = null
    private var resources: Resources? = null

    constructor(
        resources: Resources,
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0,
        attributesInput: TypedArray? = null
    ) : this(context, attrs, defStyleAttr, defStyleRes) {
        this.attributesInput = attributesInput
        this.resources = resources
        this.initializeAttributes(attrs, defStyleAttr, defStyleRes)
    }
    init {
        initializeAttributes(attrs, defStyleAttr, defStyleRes)
    }

    private fun initializeAttributes(attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        var attributes: TypedArray =
            this.attributesInput
                ?: context.obtainStyledAttributes(
                    attrs,
                    R.styleable.DoubleShadowTextClock,
                    defStyleAttr,
                    defStyleRes
                )

        var resource: Resources = this.resources ?: context.resources
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
                val addBottomPaddingToClock =
                    resource.getBoolean(R.bool.dream_overlay_complication_clock_bottom_padding)
                val metrics = paint.fontMetrics
                val padding =
                    if (addBottomPaddingToClock) {
                        textDescentExtraPadding +
                            floor(metrics.descent.toDouble()).toInt() / paddingDividedOffset
                    } else {
                        textDescentExtraPadding - floor(metrics.descent.toDouble()).toInt()
                    }
                setPaddingRelative(0, 0, 0, padding)
            }
        } finally {
            attributes.recycle()
        }
    }

    companion object {
        private val paddingDividedOffset = 2
    }

    public override fun onDraw(canvas: Canvas) {
        applyShadows(mKeyShadowInfo, mAmbientShadowInfo, this, canvas) { super.onDraw(canvas) }
    }
}
