package dev.kdrag0n.android12ext.monet.theme

import dev.kdrag0n.android12ext.monet.colors.Color
import dev.kdrag0n.android12ext.monet.colors.Oklab.Companion.toOklab
import dev.kdrag0n.android12ext.monet.colors.Oklch
import dev.kdrag0n.android12ext.monet.colors.Oklch.Companion.toOklch
import dev.kdrag0n.android12ext.monet.colors.Srgb

class DynamicColorScheme(
    targetColors: ColorScheme,
    primaryRgb8: Int,
    chromaMultiplier: Double = 1.0,
) : ColorScheme() {
    private val primaryNeutral = Srgb(primaryRgb8).toLinearSrgb().toOklab().toOklch().let { lch ->
        lch.copy(C = lch.C * chromaMultiplier)
    }
    private val primaryAccent = primaryNeutral

    // Main background color. Tinted with the primary color.
    override val neutral1 by lazy(mode = LazyThreadSafetyMode.NONE) {
        transformQuantizedColors(targetColors.neutral1, primaryNeutral)
    }

    // Secondary background color. Slightly tinted with the primary color.
    override val neutral2 by lazy(mode = LazyThreadSafetyMode.NONE) {
        transformQuantizedColors(targetColors.neutral2, primaryNeutral)
    }

    // Main accent color. Generally, this is close to the primary color.
    override val accent1 by lazy(mode = LazyThreadSafetyMode.NONE) {
        transformQuantizedColors(targetColors.accent1, primaryAccent)
    }

    // Secondary accent color. Darker shades of accent1.
    override val accent2 by lazy(mode = LazyThreadSafetyMode.NONE) {
        transformQuantizedColors(targetColors.accent2, primaryAccent)
    }

    // Tertiary accent color. Primary color shifted to the next secondary color via hue offset.
    override val accent3 by lazy(mode = LazyThreadSafetyMode.NONE) {
        transformQuantizedColors(targetColors.accent3, primaryAccent) { lch ->
            lch.copy(h = lch.h + ACCENT3_HUE_SHIFT_DEGREES)
        }
    }

    private fun transformQuantizedColors(
        colors: Map<Int, Color>,
        primary: Oklch,
        colorFilter: (Oklch) -> Oklch = { it },
    ): Map<Int, Color> {
        return colors.map { (shade, color) ->
            val target = color as? Oklch
                ?: color.toLinearSrgb().toOklab().toOklch()
            val new = colorFilter(transformColor(target, primary))
            val newColor = new.toOklab().toLinearSrgb().toSrgb()

            shade to newColor
        }.toMap()
    }

    private fun transformColor(target: Oklch, primary: Oklch): Oklch {
        return Oklch(
            // Keep target luminance. Themes should never need to change it.
            L = target.L,
            // Allow colorless gray.
            C = primary.C.coerceIn(0.0, target.C),
            // Use the primary color's hue, since it's the most prominent feature of the theme.
            h = primary.h,
        )
    }

    companion object {
        // Hue shift for the tertiary accent color (accent3), in degrees.
        // 60 degrees = shifting by a secondary color
        private const val ACCENT3_HUE_SHIFT_DEGREES = 60.0
    }
}
