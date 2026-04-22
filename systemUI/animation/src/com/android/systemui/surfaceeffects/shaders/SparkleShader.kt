/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.surfaceeffects.shaders

import android.graphics.Color
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.android.systemui.surfaceeffects.shaderutil.ShaderUtilLibrary

/**
 * Renders sparkles based on the luma matte.
 *
 * For example, you can pass in simplex noise as the luma matte and have a cloud looking sparkles.
 *
 * You may want to utilize this shader by: (Preferred) 1. Create a RuntimeShaderEffect and set the
 * [RenderEffect] to the target [View].
 * 2. Create a custom [View], set the shader to the [Paint] and use [Canvas.drawPaint] in [onDraw].
 */
class SparkleShader : RuntimeShader(SPARKLE_SHADER) {
    // language=AGSL
    companion object {
        private const val UNIFORMS =
            """
            // Used it for RenderEffect. For example:
            // myView.setRenderEffect(
            //     RenderEffect.createRuntimeShaderEffect(SparkleShader(), "in_src")
            // )
            uniform shader in_src;
            uniform half in_time;
            uniform half in_pixelate;
            uniform shader in_lumaMatte;
            layout(color) uniform vec4 in_color;
        """
        private const val MAIN_SHADER =
            """vec4 main(vec2 p) {
            half3 src = in_src.eval(p).rgb;
            half luma = getLuminosity(in_lumaMatte.eval(p).rgb);
            half sparkle = sparkles(p - mod(p, in_pixelate), in_time);
            half3 mask = maskLuminosity(in_color.rgb * sparkle, luma);

            return vec4(src * mask * in_color.a, in_color.a);
        }
        """
        private const val SPARKLE_SHADER = UNIFORMS + ShaderUtilLibrary.SHADER_LIB + MAIN_SHADER

        /** Highly recommended to use this value unless specified by design spec. */
        const val DEFAULT_SPARKLE_PIXELATE_AMOUNT = 0.8f
    }

    init {
        // Initializes the src and luma matte to be white.
        setInputShader("in_src", SolidColorShader(Color.WHITE))
        setLumaMatteColor(Color.WHITE)
    }

    /**
     * Sets the time of the sparkle animation.
     *
     * This is used for animating sparkles. Note that this only makes the sparkles sparkle in place.
     * In order to move the sparkles in x, y directions, move the luma matte input instead.
     */
    fun setTime(time: Float) {
        setFloatUniform("in_time", time)
    }

    /**
     * Sets pixelated amount of the sparkle.
     *
     * This value *must* be based on [resources.displayMetrics.density]. Otherwise, this will result
     * in having different sparkle sizes on different screens.
     *
     * Expected to be used as follows:
     * <pre>
     *     {@code
     *     val pixelDensity = context.resources.displayMetrics.density
     *     // Sparkles will be 0.8 of the pixel size.
     *     val sparkleShader = SparkleShader().apply { setPixelateAmount(pixelDensity * 0.8f) }
     *     }
     * </pre>
     */
    fun setPixelateAmount(pixelateAmount: Float) {
        setFloatUniform("in_pixelate", pixelateAmount)
    }

    /**
     * Sets the luma matte for the sparkles. The luminosity determines the sparkle's visibility.
     * Useful for setting a complex mask (e.g. simplex noise, texture, etc.)
     */
    fun setLumaMatte(lumaMatte: Shader) {
        setInputShader("in_lumaMatte", lumaMatte)
    }

    /** Sets the luma matte for the sparkles. Useful for setting a solid color. */
    fun setLumaMatteColor(color: Int) {
        setInputShader("in_lumaMatte", SolidColorShader(color))
    }

    /** Sets the color of the sparkles. Expect to have the alpha value encoded. */
    fun setColor(color: Int) {
        setColorUniform("in_color", color)
    }
}
