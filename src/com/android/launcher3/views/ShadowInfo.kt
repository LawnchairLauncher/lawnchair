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

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.R

/**
 * Launcher data holder for classes such as [DoubleShadowBubbleTextView] to model shadows for
 * "double shadow" effect.
 */
data class ShadowInfo(
    val ambientShadowBlur: Float,
    val ambientShadowColor: Int,
    val keyShadowBlur: Float,
    val keyShadowOffsetX: Float,
    val keyShadowOffsetY: Float,
    val keyShadowColor: Int
) {

    companion object {
        /** Constructs instance of ShadowInfo from Context and given attribute set. */
        @JvmStatic
        fun fromContext(context: Context, attrs: AttributeSet?, defStyle: Int): ShadowInfo {
            val styledAttrs =
                context.obtainStyledAttributes(attrs, R.styleable.ShadowInfo, defStyle, 0)
            val shadowInfo =
                ShadowInfo(
                    ambientShadowBlur =
                        styledAttrs
                            .getDimensionPixelSize(R.styleable.ShadowInfo_ambientShadowBlur, 0)
                            .toFloat(),
                    ambientShadowColor =
                        styledAttrs.getColor(R.styleable.ShadowInfo_ambientShadowColor, 0),
                    keyShadowBlur =
                        styledAttrs
                            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowBlur, 0)
                            .toFloat(),
                    keyShadowOffsetX =
                        styledAttrs
                            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowOffsetX, 0)
                            .toFloat(),
                    keyShadowOffsetY =
                        styledAttrs
                            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowOffsetY, 0)
                            .toFloat(),
                    keyShadowColor = styledAttrs.getColor(R.styleable.ShadowInfo_keyShadowColor, 0)
                )
            styledAttrs.recycle()
            return shadowInfo
        }
    }
}
