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
package com.android.systemui.surfaceeffects.turbulencenoise

import android.graphics.BlendMode
import android.graphics.Color

/** Turbulence noise animation configuration. */
data class TurbulenceNoiseAnimationConfig(
    /** The number of grids that is used to generate noise. */
    val gridCount: Float = DEFAULT_NOISE_GRID_COUNT,

    /** Multiplier for the noise luma matte. Increase this for brighter effects. */
    val luminosityMultiplier: Float = DEFAULT_LUMINOSITY_MULTIPLIER,

    /**
     * Noise move speed variables.
     *
     * Its sign determines the direction; magnitude determines the speed. <ul>
     *
     * ```
     *     <li> [noiseMoveSpeedX] positive: right to left; negative: left to right.
     *     <li> [noiseMoveSpeedY] positive: bottom to top; negative: top to bottom.
     *     <li> [noiseMoveSpeedZ] its sign doesn't matter much, as it moves in Z direction. Use it
     *     to add turbulence in place.
     * ```
     *
     * </ul>
     */
    val noiseMoveSpeedX: Float = 0f,
    val noiseMoveSpeedY: Float = 0f,
    val noiseMoveSpeedZ: Float = DEFAULT_NOISE_SPEED_Z,

    /** Color of the effect. */
    var color: Int = DEFAULT_COLOR,
    /** Background color of the effect. */
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val opacity: Int = DEFAULT_OPACITY,
    val width: Float = 0f,
    val height: Float = 0f,
    val maxDuration: Float = DEFAULT_MAX_DURATION_IN_MILLIS,
    val easeInDuration: Float = DEFAULT_EASING_DURATION_IN_MILLIS,
    val easeOutDuration: Float = DEFAULT_EASING_DURATION_IN_MILLIS,
    val pixelDensity: Float = 1f,
    val blendMode: BlendMode = DEFAULT_BLEND_MODE,
    val onAnimationEnd: Runnable? = null
) {
    companion object {
        const val DEFAULT_MAX_DURATION_IN_MILLIS = 30_000f // Max 30 sec
        const val DEFAULT_EASING_DURATION_IN_MILLIS = 750f
        const val DEFAULT_LUMINOSITY_MULTIPLIER = 1f
        const val DEFAULT_NOISE_GRID_COUNT = 1.2f
        const val DEFAULT_NOISE_SPEED_Z = 0.3f
        const val DEFAULT_OPACITY = 150 // full opacity is 255.
        const val DEFAULT_COLOR = Color.WHITE
        const val DEFAULT_BACKGROUND_COLOR = Color.BLACK
        val DEFAULT_BLEND_MODE = BlendMode.SRC_OVER
    }
}
