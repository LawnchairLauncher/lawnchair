package dev.kdrag0n.monet.theme

import android.util.Log
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.cam.Zcam
import dev.kdrag0n.colorkt.cam.Zcam.Companion.toZcam
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.gamut.LchGamut
import dev.kdrag0n.colorkt.gamut.LchGamut.clipToLinearSrgb
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.colorkt.tristimulus.CieXyz
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs.Companion.toAbs
import java.util.Objects

class DynamicColorScheme(
    targets: ColorScheme,
    private val seedColor: Color,
    private val chromaFactor: Double = 1.0,
    private val cond: Zcam.ViewingConditions,
    private val accurateShades: Boolean = true,
) : ColorScheme() {
    private val seedNeutral = seedColor.convert<CieXyz>().toAbs(cond.referenceWhite.y).toZcam(cond, include2D = false).let { lch ->
        lch.copy(chroma = lch.chroma * chromaFactor)
    }
    private val seedAccent = seedNeutral

    init {
        Log.i(TAG, "Seed color: ${seedColor.convert<Srgb>().toHex()} => $seedNeutral")
    }

    // Main accent color. Generally, this is close to the seed color.
    override val accent1 = transformSwatch(targets.accent1, seedAccent, targets.accent1)

    // Secondary accent color. Darker shades of accent1.
    override val accent2 = transformSwatch(targets.accent2, seedAccent, targets.accent1)

    // Tertiary accent color. Seed color shifted to the next secondary color via hue offset.
    override val accent3 = transformSwatch(
        swatch = targets.accent3,
        seed = seedAccent.copy(hue = seedAccent.hue + ACCENT3_HUE_SHIFT_DEGREES),
        referenceSwatch = targets.accent1,
    )

    // Main background color. Tinted with the seed color.
    override val neutral1 = transformSwatch(targets.neutral1, seedNeutral, targets.neutral1)

    // Secondary background color. Slightly tinted with the seed color.
    override val neutral2 = transformSwatch(targets.neutral2, seedNeutral, targets.neutral1)

    private fun transformSwatch(
        swatch: ColorSwatch,
        seed: Zcam,
        referenceSwatch: ColorSwatch,
    ): ColorSwatch {
        return swatch.map { (shade, color) ->
            val target = color as? Zcam
                ?: color.convert<CieXyz>().toAbs(cond.referenceWhite.y).toZcam(cond, include2D = false)
            val reference = referenceSwatch[shade]!! as? Zcam
                ?: color.convert<CieXyz>().toAbs(cond.referenceWhite.y).toZcam(cond, include2D = false)
            val newLch = transformColor(target, seed, reference)
            val newSrgb = newLch.convert<Srgb>()

            Log.d(TAG, "Transform: [$shade] $target => $newLch => ${newSrgb.toHex()}")
            shade to newSrgb
        }.toMap()
    }

    private fun transformColor(target: Zcam, seed: Zcam, reference: Zcam): Color {
        // Keep target lightness.
        val lightness = target.lightness
        // Allow colorless gray and low-chroma colors by clamping.
        // To preserve chroma ratios, scale chroma by the reference (A-1 / N-1).
        val scaleC = if (reference.chroma == 0.0) {
            // Zero reference chroma won't have chroma anyway, so use 0 to avoid a divide-by-zero
            0.0
        } else {
            // Non-zero reference chroma = possible chroma scale
            seed.chroma.coerceIn(0.0, reference.chroma) / reference.chroma
        }
        val chroma = target.chroma * scaleC
        // Use the seed color's hue, since it's the most prominent feature of the theme.
        val hue = seed.hue

        val newColor = Zcam(
            lightness = lightness,
            chroma = chroma,
            hue = hue,
            viewingConditions = cond,
        )
        return if (accurateShades) {
            newColor.clipToLinearSrgb(LchGamut.ClipMethod.PRESERVE_LIGHTNESS)
        } else {
            newColor.clipToLinearSrgb(LchGamut.ClipMethod.ADAPTIVE_TOWARDS_MID, alpha = 5.0)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is DynamicColorScheme &&
            other.seedColor == seedColor &&
            other.chromaFactor == chromaFactor &&
            other.cond == cond &&
            other.accurateShades == accurateShades
    }

    override fun hashCode() = Objects.hash(seedColor, chromaFactor, cond, accurateShades)

    companion object {
        private const val TAG = "DynamicColorScheme"

        // Hue shift for the tertiary accent color (accent3), in degrees.
        // 60 degrees = shifting by a secondary color
        private const val ACCENT3_HUE_SHIFT_DEGREES = 60.0
    }
}
