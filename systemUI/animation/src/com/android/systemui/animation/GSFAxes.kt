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

package com.android.systemui.animation

data class AxisDefinition(
    val tag: String,
    val minValue: Float,
    val defaultValue: Float,
    val maxValue: Float,
    val animationStep: Float,
)

object GSFAxes {
    @JvmStatic
    val WEIGHT =
        AxisDefinition(
            tag = "wght",
            minValue = 1f,
            defaultValue = 400f,
            maxValue = 1000f,
            animationStep = 10f,
        )

    val WIDTH =
        AxisDefinition(
            tag = "wdth",
            minValue = 25f,
            defaultValue = 100f,
            maxValue = 151f,
            animationStep = 1f,
        )

    val SLANT =
        AxisDefinition(
            tag = "slnt",
            minValue = 0f,
            defaultValue = 0f,
            maxValue = -10f,
            animationStep = 0.1f,
        )

    val ROUND =
        AxisDefinition(
            tag = "ROND",
            minValue = 0f,
            defaultValue = 0f,
            maxValue = 100f,
            animationStep = 1f,
        )

    val GRADE =
        AxisDefinition(
            tag = "GRAD",
            minValue = 0f,
            defaultValue = 0f,
            maxValue = 100f,
            animationStep = 1f,
        )

    val OPTICAL_SIZE =
        AxisDefinition(
            tag = "opsz",
            minValue = 6f,
            defaultValue = 18f,
            maxValue = 144f,
            animationStep = 1f,
        )

    // Not a GSF Axis, but present for FontInterpolator compatibility
    val ITALIC =
        AxisDefinition(
            tag = "ITAL",
            minValue = 0f,
            defaultValue = 0f,
            maxValue = 1f,
            animationStep = 0.1f,
        )

    private val AXIS_MAP =
        listOf(WEIGHT, WIDTH, SLANT, ROUND, GRADE, OPTICAL_SIZE, ITALIC)
            .map { def -> def.tag.lowercase() to def }
            .toMap()

    fun getAxis(axis: String): AxisDefinition? = AXIS_MAP[axis.lowercase()]
}
