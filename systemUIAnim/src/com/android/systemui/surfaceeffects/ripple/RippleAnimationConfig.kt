package com.android.systemui.surfaceeffects.ripple

import android.graphics.Color

/**
 * A struct that holds the ripple animation configurations.
 *
 * <p>This configuration is designed to play a SINGLE animation. Do not reuse or modify the
 * configuration parameters to play different animations, unless the value has to change within the
 * single animation (e.g. Change color or opacity during the animation). Note that this data class
 * is pulled out to make the [RippleAnimation] constructor succinct.
 */
data class RippleAnimationConfig(
    val rippleShape: RippleShader.RippleShape = RippleShader.RippleShape.CIRCLE,
    val duration: Long = 0L,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val maxWidth: Float = 0f,
    val maxHeight: Float = 0f,
    val pixelDensity: Float = 1f,
    var color: Int = Color.WHITE,
    val opacity: Int = RIPPLE_DEFAULT_ALPHA,
    val sparkleStrength: Float = RIPPLE_SPARKLE_STRENGTH,
    // Null means it uses default fade parameter values.
    val baseRingFadeParams: RippleShader.FadeParams? = null,
    val sparkleRingFadeParams: RippleShader.FadeParams? = null,
    val centerFillFadeParams: RippleShader.FadeParams? = null,
    val shouldDistort: Boolean = true
) {
    companion object {
        const val RIPPLE_SPARKLE_STRENGTH: Float = 0.3f
        const val RIPPLE_DEFAULT_COLOR: Int = 0xffffffff.toInt()
        const val RIPPLE_DEFAULT_ALPHA: Int = 115 // full opacity is 255.
    }
}
