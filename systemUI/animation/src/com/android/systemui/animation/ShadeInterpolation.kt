package com.android.systemui.animation

import android.util.MathUtils

object ShadeInterpolation {

    /**
     * Interpolate alpha for notification background scrim during shade expansion.
     *
     * @param fraction Shade expansion fraction
     */
    @JvmStatic
    fun getNotificationScrimAlpha(fraction: Float): Float {
        val mappedFraction = MathUtils.constrainedMap(0f, 1f, 0f, 0.5f, fraction)
        return interpolateEaseInOut(mappedFraction)
    }

    /**
     * Interpolate alpha for shade content during shade expansion.
     *
     * @param fraction Shade expansion fraction
     */
    @JvmStatic
    fun getContentAlpha(fraction: Float): Float {
        val mappedFraction = MathUtils.constrainedMap(0f, 1f, 0.3f, 1f, fraction)
        return interpolateEaseInOut(mappedFraction)
    }

    private fun interpolateEaseInOut(fraction: Float): Float {
        val mappedFraction = fraction * 1.2f - 0.2f
        return if (mappedFraction <= 0) {
            0f
        } else {
            val oneMinusFrac = 1f - mappedFraction
            (1f - 0.5f * (1f - Math.cos((3.14159f * oneMinusFrac * oneMinusFrac).toDouble())))
                .toFloat()
        }
    }
}
