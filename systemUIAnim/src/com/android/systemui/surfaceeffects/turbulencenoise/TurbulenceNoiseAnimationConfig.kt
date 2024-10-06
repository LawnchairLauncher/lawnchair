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

import android.graphics.Color
import java.util.Random

/** Turbulence noise animation configuration. */
data class TurbulenceNoiseAnimationConfig(
    /** The number of grids that is used to generate noise. */
    val gridCount: Float = DEFAULT_NOISE_GRID_COUNT,

    /** Multiplier for the noise luma matte. Increase this for brighter effects. */
    val luminosityMultiplier: Float = DEFAULT_LUMINOSITY_MULTIPLIER,

    /** Initial noise offsets. */
    val noiseOffsetX: Float = random.nextFloat(),
    val noiseOffsetY: Float = random.nextFloat(),
    val noiseOffsetZ: Float = random.nextFloat(),

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
    val color: Int = DEFAULT_COLOR,
    /** Background color of the effect. */
    val screenColor: Int = DEFAULT_SCREEN_COLOR,
    val width: Float = 0f,
    val height: Float = 0f,
    val maxDuration: Float = DEFAULT_MAX_DURATION_IN_MILLIS,
    val easeInDuration: Float = DEFAULT_EASING_DURATION_IN_MILLIS,
    val easeOutDuration: Float = DEFAULT_EASING_DURATION_IN_MILLIS,
    val pixelDensity: Float = 1f,
    /**
     * Variants in noise. Higher number means more contrast; lower number means less contrast but
     * make the noise dimmed. You may want to increase the [lumaMatteBlendFactor] to compensate.
     * Expected range [0, 1].
     */
    val lumaMatteBlendFactor: Float = DEFAULT_LUMA_MATTE_BLEND_FACTOR,
    /**
     * Offset for the overall brightness in noise. Higher number makes the noise brighter. You may
     * want to use this if you have made the noise softer using [lumaMatteBlendFactor]. Expected
     * range [0, 1].
     */
    val lumaMatteOverallBrightness: Float = DEFAULT_LUMA_MATTE_OVERALL_BRIGHTNESS,
    /** Whether to flip the luma mask. */
    val shouldInverseNoiseLuminosity: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAX_DURATION_IN_MILLIS = 30_000f // Max 30 sec
        const val DEFAULT_EASING_DURATION_IN_MILLIS = 750f
        const val DEFAULT_LUMINOSITY_MULTIPLIER = 1f
        const val DEFAULT_NOISE_GRID_COUNT = 1.2f
        const val DEFAULT_NOISE_SPEED_Z = 0.3f
        const val DEFAULT_COLOR = Color.WHITE
        const val DEFAULT_LUMA_MATTE_BLEND_FACTOR = 1f
        const val DEFAULT_LUMA_MATTE_OVERALL_BRIGHTNESS = 0f
        const val DEFAULT_SCREEN_COLOR = Color.BLACK
        private val random = Random()
    }
}
