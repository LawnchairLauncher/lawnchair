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

import android.graphics.RuntimeShader
import com.android.systemui.surfaceeffects.shaderutil.ShaderUtilLibrary
import java.lang.Float.max

/**
 * Shader that renders turbulence simplex noise, by default no octave.
 *
 * @param useFractal whether to use fractal noise (4 octaves).
 */
class TurbulenceNoiseShader(useFractal: Boolean = false) :
    RuntimeShader(if (useFractal) FRACTAL_NOISE_SHADER else SIMPLEX_NOISE_SHADER) {
    // language=AGSL
    companion object {
        private const val UNIFORMS =
            """
            uniform float in_gridNum;
            uniform vec3 in_noiseMove;
            uniform vec2 in_size;
            uniform float in_aspectRatio;
            uniform float in_opacity;
            uniform float in_pixelDensity;
            uniform float in_inverseLuma;
            uniform half in_lumaMatteBlendFactor;
            uniform half in_lumaMatteOverallBrightness;
            layout(color) uniform vec4 in_color;
            layout(color) uniform vec4 in_backgroundColor;
        """

        private const val SIMPLEX_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                // Bring it to [0, 1] range.
                float luma = (simplex3d(noiseP) * in_inverseLuma) * 0.5 + 0.5;
                luma = saturate(luma * in_lumaMatteBlendFactor + in_lumaMatteOverallBrightness)
                        * in_opacity;
                vec3 mask = maskLuminosity(in_color.rgb, luma);
                vec3 color = in_backgroundColor.rgb + mask * 0.6;

                // Add dither with triangle distribution to avoid color banding. Dither in the
                // shader here as we are in gamma space.
                float dither = triangleNoise(p * in_pixelDensity) / 255.;

                // The result color should be pre-multiplied, i.e. [R*A, G*A, B*A, A], thus need to
                // multiply rgb with a to get the correct result.
                color = (color + dither.rrr) * in_opacity;
                return vec4(color, in_opacity);
            }
        """

        private const val FRACTAL_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                // Bring it to [0, 1] range.
                float luma = (simplex3d_fractal(noiseP) * in_inverseLuma) * 0.5 + 0.5;
                luma = saturate(luma * in_lumaMatteBlendFactor + in_lumaMatteOverallBrightness)
                        * in_opacity;
                vec3 mask = maskLuminosity(in_color.rgb, luma);
                vec3 color = in_backgroundColor.rgb + mask * 0.6;

                // Skip dithering.
                return vec4(color * in_opacity, in_opacity);
            }
        """

        private const val SIMPLEX_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + SIMPLEX_SHADER
        private const val FRACTAL_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + FRACTAL_SHADER
    }

    /** Sets the number of grid for generating noise. */
    fun setGridCount(gridNumber: Float = 1.0f) {
        setFloatUniform("in_gridNum", gridNumber)
    }

    /**
     * Sets the pixel density of the screen.
     *
     * Used it for noise dithering.
     */
    fun setPixelDensity(pixelDensity: Float) {
        setFloatUniform("in_pixelDensity", pixelDensity)
    }

    /** Sets the noise color of the effect. */
    fun setColor(color: Int) {
        setColorUniform("in_color", color)
    }

    /** Sets the background color of the effect. */
    fun setBackgroundColor(color: Int) {
        setColorUniform("in_backgroundColor", color)
    }

    /**
     * Sets the opacity to achieve fade in/ out of the animation.
     *
     * Expected value range is [1, 0].
     */
    fun setOpacity(opacity: Float) {
        setFloatUniform("in_opacity", opacity)
    }

    /** Sets the size of the shader. */
    fun setSize(width: Float, height: Float) {
        setFloatUniform("in_size", width, height)
        setFloatUniform("in_aspectRatio", width / max(height, 0.001f))
    }

    /**
     * Sets blend and brightness factors of the luma matte.
     *
     * @param lumaMatteBlendFactor increases or decreases the amount of variance in noise. Setting
     *   this a lower number removes variations. I.e. the turbulence noise will look more blended.
     *   Expected input range is [0, 1]. more dimmed.
     * @param lumaMatteOverallBrightness adds the overall brightness of the turbulence noise.
     *   Expected input range is [0, 1].
     *
     * Example usage: You may want to apply a small number to [lumaMatteBlendFactor], such as 0.2,
     * which makes the noise look softer. However it makes the overall noise look dim, so you want
     * offset something like 0.3 for [lumaMatteOverallBrightness] to bring back its overall
     * brightness.
     */
    fun setLumaMatteFactors(
        lumaMatteBlendFactor: Float = 1f,
        lumaMatteOverallBrightness: Float = 0f
    ) {
        setFloatUniform("in_lumaMatteBlendFactor", lumaMatteBlendFactor)
        setFloatUniform("in_lumaMatteOverallBrightness", lumaMatteOverallBrightness)
    }

    /**
     * Sets whether to inverse the luminosity of the noise.
     *
     * By default noise will be used as a luma matte as is. This means that you will see color in
     * the brighter area. If you want to invert it, meaning blend color onto the darker side, set to
     * true.
     */
    fun setInverseNoiseLuminosity(inverse: Boolean) {
        setFloatUniform("in_inverseLuma", if (inverse) -1f else 1f)
    }

    /** Current noise movements in x, y, and z axes. */
    var noiseOffsetX: Float = 0f
        private set
    var noiseOffsetY: Float = 0f
        private set
    var noiseOffsetZ: Float = 0f
        private set

    /** Sets noise move offset in x, y, and z direction. */
    fun setNoiseMove(x: Float, y: Float, z: Float) {
        noiseOffsetX = x
        noiseOffsetY = y
        noiseOffsetZ = z
        setFloatUniform("in_noiseMove", noiseOffsetX, noiseOffsetY, noiseOffsetZ)
    }
}
