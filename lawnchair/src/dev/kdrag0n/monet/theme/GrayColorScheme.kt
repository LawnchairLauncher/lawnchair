package dev.kdrag0n.monet.theme

import dev.kdrag0n.colorkt.rgb.Srgb
import java.util.Objects

class GrayColorScheme(private val accentColorScheme: ColorScheme) : ColorScheme() {

    override val neutral1 = mapOf(
        0 to Srgb(0xffffff),
        10 to Srgb(0xfbfbfb),
        50 to Srgb(0xf0f0f0),
        100 to Srgb(0xe2e2e2),
        200 to Srgb(0xc6c6c6),
        300 to Srgb(0xababab),
        400 to Srgb(0x909090),
        500 to Srgb(0x757575),
        600 to Srgb(0x5e5e5e),
        700 to Srgb(0x464646),
        800 to Srgb(0x303030),
        900 to Srgb(0x1b1b1b),
        1000 to Srgb(0x000000),
    )

    override val neutral2 = mapOf(
        0 to Srgb(0xffffff),
        10 to Srgb(0xfbfbfb),
        50 to Srgb(0xf0f0f0),
        100 to Srgb(0xe2e2e2),
        200 to Srgb(0xc6c6c6),
        300 to Srgb(0xababab),
        400 to Srgb(0x909090),
        500 to Srgb(0x757575),
        600 to Srgb(0x5e5e5e),
        700 to Srgb(0x464646),
        800 to Srgb(0x303030),
        900 to Srgb(0x1b1b1b),
        1000 to Srgb(0x000000),
    )

    override val accent1 = accentColorScheme.accent1
    override val accent2 = accentColorScheme.accent2
    override val accent3 = accentColorScheme.accent3

    override fun equals(other: Any?) = other is GrayColorScheme && other.accentColorScheme == accentColorScheme

    override fun hashCode() = Objects.hash(accentColorScheme)
}
