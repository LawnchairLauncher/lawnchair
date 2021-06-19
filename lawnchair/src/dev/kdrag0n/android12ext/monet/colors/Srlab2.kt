package dev.kdrag0n.android12ext.monet.colors

import kotlin.math.pow

data class Srlab2(
    override val L: Double,
    override val a: Double,
    override val b: Double,
) : Color, Lab {
    override fun toLinearSrgb(): LinearSrgb {
        val x2 = 0.01 * L + 0.000904127 * a + 0.000456344 * b
        val y2 = 0.01 * L - 0.000533159 * a - 0.000269178 * b
        val z2 = 0.01 * L                   - 0.005800000 * b

        val x = cube(x2)
        val y = cube(y2)
        val z = cube(z2)

        return LinearSrgb(
            r =  5.435679 * x - 4.599131 * y + 0.163593 * z,
            g = -1.168090 * x + 2.327977 * y - 0.159798 * z,
            b =  0.037840 * x - 0.198564 * y + 1.160644 * z,
        )
    }

    companion object {
        private fun root(x: Double) = if (x <= 216.0 / 24389.0) {
            x * 24389.0 / 2700.0
        } else {
            1.16 * Math.cbrt(x) - 0.16
        }

        private fun cube(x: Double) = if (x <= 0.08) {
            x * 2700.0 / 24389.0
        } else {
            ((x + 0.16) / 1.16).pow(3)
        }

        fun LinearSrgb.toSrlab2(): Srlab2 {
            val x = 0.320530 * r + 0.636920 * g + 0.042560 * b
            val y = 0.161987 * r + 0.756636 * g + 0.081376 * b
            val z = 0.017228 * r + 0.108660 * g + 0.874112 * b

            val x2 = root(x)
            val y2 = root(y)
            val z2 = root(z)

            return Srlab2(
                L =  37.0950 * x2 +  62.9054 * y2 -   0.0008 * z2,
                a = 663.4684 * x2 - 750.5078 * y2 +  87.0328 * z2,
                b =  63.9569 * x2 + 108.4576 * y2 - 172.4152 * z2,
            )
        }
    }
}
