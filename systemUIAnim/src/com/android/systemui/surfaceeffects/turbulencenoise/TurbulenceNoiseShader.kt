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

/** Shader that renders turbulence simplex noise, with no octave. */
class TurbulenceNoiseShader : RuntimeShader(TURBULENCE_NOISE_SHADER) {
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
            layout(color) uniform vec4 in_color;
            layout(color) uniform vec4 in_backgroundColor;
        """

        private const val SHADER_LIB =
            """
            float getLuminosity(vec3 c) {
                return 0.3*c.r + 0.59*c.g + 0.11*c.b;
            }

            vec3 maskLuminosity(vec3 dest, float lum) {
                dest.rgb *= vec3(lum);
                // Clip back into the legal range
                dest = clamp(dest, vec3(0.), vec3(1.0));
                return dest;
            }
        """

        private const val MAIN_SHADER =
            """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                float luma = simplex3d(noiseP) * in_opacity;
                vec3 mask = maskLuminosity(in_color.rgb, luma);
                vec3 color = in_backgroundColor.rgb + mask * 0.6;

                // Add dither with triangle distribution to avoid color banding. Ok to dither in the
                // shader here as we are in gamma space.
                float dither = triangleNoise(p * in_pixelDensity) / 255.;

                // The result color should be pre-multiplied, i.e. [R*A, G*A, B*A, A], thus need to
                // multiply rgb with a to get the correct result.
                color = (color + dither.rrr) * in_color.a;
                return vec4(color, in_color.a);
            }
        """

        private const val TURBULENCE_NOISE_SHADER =
            ShaderUtilLibrary.SHADER_LIB + UNIFORMS + SHADER_LIB + MAIN_SHADER
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
