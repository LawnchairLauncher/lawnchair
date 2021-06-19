package dev.kdrag0n.android12ext.monet.colors

import kotlin.math.*

interface Lch {
    val L: Double
    val C: Double
    val h: Double

    companion object {
        internal fun Lab.toLch(): Triple<Double, Double, Double> {
            val hDeg = Math.toDegrees(atan2(b, a))

            return Triple(
                L,
                sqrt(a.pow(2) + b.pow(2)),
                // Normalize the angle, as many will be negative
                if (hDeg < 0) hDeg + 360 else hDeg,
            )
        }

        internal fun Lch.toLab(): Triple<Double, Double, Double> {
            val hRad = Math.toRadians(h)

            return Triple(
                L,
                C * cos(hRad),
                C * sin(hRad),
            )
        }
    }
}
