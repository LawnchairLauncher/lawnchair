package dev.kdrag0n.monet.theme

import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.cam.Zcam
import dev.kdrag0n.colorkt.cam.Zcam.Companion.toZcam
import dev.kdrag0n.colorkt.rgb.LinearSrgb.Companion.toLinear
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.colorkt.tristimulus.CieXyz.Companion.toXyz
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs.Companion.toAbs
import dev.kdrag0n.colorkt.ucs.lab.CieLab

/*
 * Default target colors, conforming to Material You standards.
 *
 * Derived from AOSP and Pixel defaults.
 */
class MaterialYouTargets(
    private val chromaFactor: Double = 1.0,
    useLinearLightness: Boolean,
    val cond: Zcam.ViewingConditions,
) : ColorScheme() {
    companion object {
        // Linear ZCAM lightness
        private val LINEAR_LIGHTNESS_MAP = mapOf(
            0    to 100.0,
            10   to  99.0,
            20   to  98.0,
            50   to  95.0,
            100  to  90.0,
            200  to  80.0,
            300  to  70.0,
            400  to  60.0,
            500  to  50.0,
            600  to  40.0,
            650  to  35.0,
            700  to  30.0,
            800  to  20.0,
            900  to  10.0,
            950  to   5.0,
            1000 to   0.0,
        )

        // CIELAB lightness from AOSP defaults
        private val CIELAB_LIGHTNESS_MAP = LINEAR_LIGHTNESS_MAP
            .map { it.key to if (it.value == 50.0) 49.6 else it.value }
            .toMap()

        // Accent colors from Pixel defaults
        private val REF_ACCENT1_COLORS = listOf(
            0xd3e3fd,
            0xa8c7fa,
            0x7cacf8,
            0x4c8df6,
            0x1b6ef3,
            0x0b57d0,
            0x0842a0,
            0x062e6f,
            0x041e49,
        )

        private const val ACCENT1_REF_CHROMA_FACTOR = 1.2
    }

    override val neutral1: ColorSwatch
    override val neutral2: ColorSwatch

    override val accent1: ColorSwatch
    override val accent2: ColorSwatch
    override val accent3: ColorSwatch

    init {
        val lightnessMap = if (useLinearLightness) {
            LINEAR_LIGHTNESS_MAP
        } else {
            CIELAB_LIGHTNESS_MAP
                .map { it.key to cielabL(it.value) }
                .toMap()
        }

        // Accent chroma from Pixel defaults
        // We use the most chromatic color as the reference
        // A-1 chroma = avg(default Pixel Blue shades 100-900)
        // Excluding very bright variants (10, 50) to avoid light bias
        // A-1 > A-3 > A-2
        val accent1Chroma = calcAccent1Chroma() * ACCENT1_REF_CHROMA_FACTOR
        val accent2Chroma = accent1Chroma / 3
        val accent3Chroma = accent2Chroma * 2

        // Custom neutral chroma
        val neutral1Chroma = accent1Chroma / 8
        val neutral2Chroma = accent1Chroma / 5

        neutral1 = shadesWithChroma(neutral1Chroma, lightnessMap)
        neutral2 = shadesWithChroma(neutral2Chroma, lightnessMap)

        accent1 = shadesWithChroma(accent1Chroma, lightnessMap)
        accent2 = shadesWithChroma(accent2Chroma, lightnessMap)
        accent3 = shadesWithChroma(accent3Chroma, lightnessMap)
    }

    private fun cielabL(l: Double) = CieLab(
        L = l,
        a = 0.0,
        b = 0.0,
    ).toXyz().toAbs(cond.referenceWhite.y).toZcam(cond, include2D = false).lightness

    private fun calcAccent1Chroma() = REF_ACCENT1_COLORS
        .map { Srgb(it).toLinear().toXyz().toAbs(cond.referenceWhite.y).toZcam(cond, include2D = false).chroma }
        .average()

    private fun shadesWithChroma(
        chroma: Double,
        lightnessMap: Map<Int, Double>,
    ): Map<Int, Color> {
        // Adjusted chroma
        val chromaAdj = chroma * chromaFactor

        return lightnessMap.map {
            it.key to Zcam(
                lightness = it.value,
                chroma = chromaAdj,
                hue = 0.0,
                viewingConditions = cond,
            )
        }.toMap()
    }
}
