package dev.kdrag0n.android12ext.monet.colors

import kotlin.math.pow

data class LinearSrgb(
    val r: Double,
    val g: Double,
    val b: Double,
) : Color {
    override fun toLinearSrgb() = this

    override fun toSrgb(): Srgb {
        return Srgb(
            r = oetf(r),
            g = oetf(g),
            b = oetf(b),
        )
    }

    companion object {
        // Opto-electrical transfer function
        // Forward transform to sRGB
        private fun oetf(x: Double): Double {
            return if (x >= 0.0031308) {
                1.055 * x.pow(1.0 / 2.4) - 0.055
            } else {
                12.92 * x
            }
        }

        // Electro-optical transfer function
        // Inverse transform to linear sRGB
        private fun eotf(x: Double): Double {
            return if (x >= 0.04045) {
                ((x + 0.055) / 1.055).pow(2.4)
            } else {
                x / 12.92
            }
        }

        fun Srgb.toLinearSrgb(): LinearSrgb {
            return LinearSrgb(
                r = eotf(r),
                g = eotf(g),
                b = eotf(b),
            )
        }
    }
}
