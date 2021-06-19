package dev.kdrag0n.android12ext.monet.theme

import dev.kdrag0n.android12ext.monet.colors.Color
import dev.kdrag0n.android12ext.monet.colors.Oklch

/*
 * Default target colors, conforming to Material You standards.
 *
 * Mostly derived from:
 *   - AOSP defaults: Untinted gray neutral colors and teal accent (+60 deg = ~purple).
 *   - Pixel defaults: Neutral colors are equivalent to AOSP. Main accent is blue.
 */
class TargetColors(
    private val chromaFactor: Double = 1.0,
) : ColorScheme() {
    companion object {
        // Lightness from AOSP defaults
        private const val L_0    = 1.00
        private const val L_50   = 0.96
        private const val L_100  = 0.91
        private const val L_200  = 0.83
        private const val L_300  = 0.74
        private const val L_400  = 0.65
        private const val L_500  = 0.56
        private const val L_600  = 0.48
        private const val L_700  = 0.39
        private const val L_800  = 0.31
        private const val L_900  = 0.22
        private const val L_1000 = 0.00

        // Neutral chroma from Google's CAM16 implementation
        private const val NEUTRAL1_CHROMA = 0.0132
        private const val NEUTRAL2_CHROMA = NEUTRAL1_CHROMA / 2

        // Accent chroma from Pixel defaults
        private const val ACCENT1_CHROMA = 0.1212
        private const val ACCENT2_CHROMA = 0.04
        private const val ACCENT3_CHROMA = 0.06
    }

    override val neutral1 = shadesWithChroma(NEUTRAL1_CHROMA)
    override val neutral2 = shadesWithChroma(NEUTRAL2_CHROMA)

    override val accent1 = shadesWithChroma(ACCENT1_CHROMA)
    override val accent2 = shadesWithChroma(ACCENT2_CHROMA)
    override val accent3 = shadesWithChroma(ACCENT3_CHROMA)

    private fun shadesWithChroma(chroma: Double): Map<Int, Color> {
        val chromaAdj = chroma * chromaFactor
        return mapOf(
            0    to Oklch(L_0,    0.0),
            50   to Oklch(L_50,   chromaAdj),
            100  to Oklch(L_100,  chromaAdj),
            200  to Oklch(L_200,  chromaAdj),
            300  to Oklch(L_300,  chromaAdj),
            400  to Oklch(L_400,  chromaAdj),
            500  to Oklch(L_500,  chromaAdj),
            600  to Oklch(L_600,  chromaAdj),
            700  to Oklch(L_700,  chromaAdj),
            800  to Oklch(L_800,  chromaAdj),
            900  to Oklch(L_900,  chromaAdj),
            1000 to Oklch(L_1000, 0.0),
        )
    }
}
