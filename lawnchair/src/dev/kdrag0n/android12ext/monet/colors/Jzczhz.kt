package dev.kdrag0n.android12ext.monet.colors

import dev.kdrag0n.android12ext.monet.colors.Lch.Companion.toLab
import dev.kdrag0n.android12ext.monet.colors.Lch.Companion.toLch

data class Jzczhz(
    override val L: Double,
    override val C: Double,
    override val h: Double = 0.0,
) : Color, Lch {
    override fun toLinearSrgb() = toJzazbz().toLinearSrgb()

    fun toJzazbz(): Jzazbz {
        val (l, a, b) = toLab()
        return Jzazbz(l, a, b)
    }

    companion object {
        fun Jzazbz.toJzczhz(): Jzczhz {
            val (l, c, h) = toLch()
            return Jzczhz(l, c, h)
        }
    }
}
