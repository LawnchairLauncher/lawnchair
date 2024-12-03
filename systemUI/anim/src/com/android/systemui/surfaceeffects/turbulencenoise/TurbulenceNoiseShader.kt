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
import com.android.systemui.surfaceeffects.shaders.SolidColorShader
import com.android.systemui.surfaceeffects.shaderutil.ShaderUtilLibrary
import java.lang.Float.max

/**
 * Shader that renders turbulence simplex noise, by default no octave.
 *
 * @param baseType the base [Type] of the shader.
 */
class TurbulenceNoiseShader(val baseType: Type = Type.SIMPLEX_NOISE) :
    RuntimeShader(getShader(baseType)) {
    // language=AGSL
    companion object {
        /** Uniform name for the background buffer (e.g. image, solid color, etc.). */
        const val BACKGROUND_UNIFORM = "in_src"
        private const val UNIFORMS =
            """
            uniform shader ${BACKGROUND_UNIFORM};
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
            layout(color) uniform vec4 in_screenColor;
        """

        private const val SIMPLEX_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                // Compute turbulence effect with the uv distorted with simplex noise.
                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                vec3 color = getColorTurbulenceMask(simplex3d(noiseP) * in_inverseLuma);

                // Blend the result with the background color.
                color = in_src.eval(p).rgb + color * 0.6;

                // Add dither with triangle distribution to avoid color banding. Dither in the
                // shader here as we are in gamma space.
                float dither = triangleNoise(p * in_pixelDensity) / 255.;
                color += dither.rrr;

                // Return the pre-multiplied alpha result, i.e. [R*A, G*A, B*A, A].
                return vec4(color * in_opacity, in_opacity);
            }
        """

        private const val FRACTAL_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                vec3 color = getColorTurbulenceMask(simplex3d_fractal(noiseP) * in_inverseLuma);

                // Blend the result with the background color.
                color = in_src.eval(p).rgb + color * 0.6;

                // Skip dithering.
                return vec4(color * in_opacity, in_opacity);
            }
        """

        /**
         * This effect has two layers: color turbulence effect with sparkles on top.
         * 1. Gets the luma matte using Simplex noise.
         * 2. Generate a colored turbulence layer with the luma matte.
         * 3. Generate a colored sparkle layer with the same luma matter.
         * 4. Apply a screen color to the background image.
         * 5. Composite the previous result with the color turbulence.
         * 6. Composite the latest result with the sparkles.
         */
        private const val SIMPLEX_SPARKLE_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                // Luma is used for both color and sparkle masks.
                float luma = simplex3d(noiseP) * in_inverseLuma;

                // Get color layer (color mask with in_color applied)
                vec3 colorLayer = getColorTurbulenceMask(simplex3d(noiseP) * in_inverseLuma);
                float dither = triangleNoise(p * in_pixelDensity) / 255.;
                colorLayer += dither.rrr;

                // Get sparkle layer (sparkle mask with particles & in_color applied)
                vec3 sparkleLayer = getSparkleTurbulenceMask(luma, p);

                // Composite with the background.
                half4 bgColor = in_src.eval(p);
                half sparkleOpacity = smoothstep(0, 0.75, in_opacity);

                half3 effect = screen(bgColor.rgb, in_screenColor.rgb);
                effect = screen(effect, colorLayer * 0.22);
                effect += sparkleLayer * sparkleOpacity;

                return mix(bgColor, vec4(effect, 1.), in_opacity);
            }
        """

        private const val COMMON_FUNCTIONS =
            /**
             * Below two functions generate turbulence layers (color or sparkles applied) with the
             * given luma matte. They both return a mask with in_color applied.
             */
            """
            vec3 getColorTurbulenceMask(float luma) {
                // Bring it to [0, 1] range.
                luma = luma * 0.5 + 0.5;

                half colorLuma =
                    saturate(luma * in_lumaMatteBlendFactor + in_lumaMatteOverallBrightness)
                    * in_opacity;
                vec3 colorLayer = maskLuminosity(in_color.rgb, colorLuma);

                return colorLayer;
            }

            vec3 getSparkleTurbulenceMask(float luma, vec2 p) {
                half lumaIntensity = 1.75;
                half lumaBrightness = -1.3;
                half sparkleLuma = max(luma * lumaIntensity + lumaBrightness, 0.);

                float sparkle = sparkles(p - mod(p, in_pixelDensity * 0.8), in_noiseMove.z);
                vec3 sparkleLayer = maskLuminosity(in_color.rgb * sparkle, sparkleLuma);

                return sparkleLayer;
            }
        """
        private const val SIMPLEX_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + COMMON_FUNCTIONS + SIMPLEX_SHADER
        private const val FRACTAL_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + COMMON_FUNCTIONS + FRACTAL_SHADER
        private const val SPARKLE_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + COMMON_FUNCTIONS + SIMPLEX_SPARKLE_SHADER

        enum class Type {
            /** Effect with a simple color noise turbulence. */
            SIMPLEX_NOISE,
            /** Effect with a simple color noise turbulence, with fractal. */
            SIMPLEX_NOISE_FRACTAL,
            /** Effect with color & sparkle turbulence with screen color layer. */
            SIMPLEX_NOISE_SPARKLE
        }

        fun getShader(type: Type): String {
            return when (type) {
                Type.SIMPLEX_NOISE -> SIMPLEX_NOISE_SHADER
                Type.SIMPLEX_NOISE_FRACTAL -> FRACTAL_NOISE_SHADER
                Type.SIMPLEX_NOISE_SPARKLE -> SPARKLE_NOISE_SHADER
            }
        }
    }

    /** Convenient way for updating multiple uniform values via config object. */
    fun applyConfig(config: TurbulenceNoiseAnimationConfig) {
        setGridCount(config.gridCount)
        setPixelDensity(config.pixelDensity)
        setColor(config.color)
        setScreenColor(config.screenColor)
        setSize(config.width, config.height)
        setLumaMatteFactors(config.lumaMatteBlendFactor, config.lumaMatteOverallBrightness)
        setInverseNoiseLuminosity(config.shouldInverseNoiseLuminosity)
        setNoiseMove(config.noiseOffsetX, config.noiseOffsetY, config.noiseOffsetZ)
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

    /** Sets the noise color of the effect. Alpha is ignored. */
    fun setColor(color: Int) {
        setColorUniform("in_color", color)
    }

    /**
     * Sets the color that is used for blending on top of the background color/image. Only relevant
     * to [Type.SIMPLEX_NOISE_SPARKLE].
     */
    fun setScreenColor(color: Int) {
        setColorUniform("in_screenColor", color)
    }

    /**
     * Sets the background color of the effect. Alpha is ignored. If you are using [RenderEffect],
     * no need to call this function since the background image of the View will be used.
     */
    fun setBackgroundColor(color: Int) {
        setInputShader(BACKGROUND_UNIFORM, SolidColorShader(color))
    }

    /**
     * Sets the opacity of the effect. Not intended to set by the client as it is used for
     * ease-in/out animations.
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
     *   Expected input range is [0, 1].
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
