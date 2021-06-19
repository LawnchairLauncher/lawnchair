package dev.kdrag0n.android12ext.monet.colors

data class CieXyz(
    val x: Double,
    val y: Double,
    val z: Double,
) : Color {
    override fun toLinearSrgb(): LinearSrgb {
        return LinearSrgb(
            r = +3.2404542 * x + -1.5371385 * y + -0.4985314 * z,
            g = -0.9692660 * x + +1.8760108 * y + +0.0415560 * z,
            b = +0.0556434 * x + -0.2040259 * y + +1.0572252 * z,
        )
    }

    companion object {
        fun LinearSrgb.toCieXyz(): CieXyz {
            return CieXyz(
                x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b,
                y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b,
            )
        }
    }
}
