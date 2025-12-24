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

package com.android.quickstep.util

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.util.Log
import com.android.wm.shell.shared.TypefaceUtils

object FontUtils {
    private const val TAG = "FontUtils"

    private val baseTypeface =
        Typeface.create(TypefaceUtils.FontFamily.GSF_LABEL_LARGE.value, Typeface.NORMAL)

    @JvmStatic
    fun getTypeFace(resources: Resources): Typeface =
        Typeface.create(baseTypeface, getFontWeight(resources), /* italic= */ false)

    fun getFontWeight(resources: Resources): Int {
        val fontWeightAdjustment: Int = resources.configuration.fontWeightAdjustment
        val adjusted =
            if (fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) {
                Typeface.Builder.NORMAL_WEIGHT + fontWeightAdjustment
            } else {
                Typeface.Builder.NORMAL_WEIGHT
            }
        if (adjusted !in 0..1000) {
            Log.w(
                TAG,
                "Calculated font weight $adjusted is out of bounds (0-1000) " +
                    "after adjustment: $fontWeightAdjustment.",
            )
        }
        return adjusted.coerceIn(0, 1000)
    }
}
