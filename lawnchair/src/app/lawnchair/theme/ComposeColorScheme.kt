package app.lawnchair.theme

import androidx.annotation.FloatRange
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.core.math.MathUtils
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Set the luminance(tone) of this color. Chroma may decrease because chroma has a different maximum
 * for any given hue and luminance.
 *
 * @param newLuminance 0 <= newLuminance <= 100; invalid values are corrected.
 */
internal fun Color.setLuminance(@FloatRange(from = 0.0, to = 100.0) newLuminance: Float): Color {
    if ((newLuminance < 0.0001) or (newLuminance > 99.9999)) {
        // aRGBFromLstar() from monet ColorUtil.java
        val y = 100 * labInvf((newLuminance + 16) / 116)
        val component = delinearized(y)
        return Color(
            /* red = */
            component,
            /* green = */
            component,
            /* blue = */
            component,
        )
    }

    val sLAB = this.convert(ColorSpaces.CieLab)
    return Color(
        /* luminance = */
        newLuminance,
        /* a = */
        sLAB.component2(),
        /* b = */
        sLAB.component3(),
        colorSpace = ColorSpaces.CieLab,
    )
        .convert(ColorSpaces.Srgb)
}

/** Helper method from monet ColorUtils.java */
private fun labInvf(ft: Float): Float {
    val e = 216f / 24389f
    val kappa = 24389f / 27f
    val ft3 = ft * ft * ft
    return if (ft3 > e) {
        ft3
    } else {
        (116 * ft - 16) / kappa
    }
}

/**
 * Helper method from monet ColorUtils.java
 *
 * Delinearizes an RGB component.
 *
 * @param rgbComponent 0.0 <= rgb_component <= 100.0, represents linear R/G/B channel
 * @return 0 <= output <= 255, color channel converted to regular RGB space
 */
private fun delinearized(rgbComponent: Float): Int {
    val normalized = rgbComponent / 100
    val delinearized =
        if (normalized <= 0.0031308) {
            normalized * 12.92
        } else {
            1.055 * normalized.toDouble().pow(1.0 / 2.4) - 0.055
        }
    return MathUtils.clamp((delinearized * 255.0).roundToInt(), 0, 255)
}

@Composable
fun dev.kdrag0n.monet.theme.ColorScheme.toComposeColorScheme(isDark: Boolean): ColorScheme = remember(this, isDark) {
    val neutralVariant4 = neutralVariant(40).setLuminance(4f)
    val neutralVariant6 = neutralVariant(40).setLuminance(6f)
    val neutralVariant12 = neutralVariant(40).setLuminance(12f)
    val neutralVariant17 = neutralVariant(40).setLuminance(17f)
    val neutralVariant22 = neutralVariant(40).setLuminance(22f)
    val neutralVariant24 = neutralVariant(40).setLuminance(24f)
    val neutralVariant87 = neutralVariant(40).setLuminance(87f)
    val neutralVariant92 = neutralVariant(40).setLuminance(92f)
    val neutralVariant94 = neutralVariant(40).setLuminance(94f)
    val neutralVariant96 = neutralVariant(40).setLuminance(96f)
    val neutralVariant98 = neutralVariant(40).setLuminance(98f)

    if (isDark) {
        darkColorScheme(
            primary = primary(80),
            onPrimary = primary(20),
            primaryContainer = primary(30),
            onPrimaryContainer = primary(90),
            inversePrimary = primary(40),
            secondary = secondary(80),
            onSecondary = secondary(20),
            secondaryContainer = secondary(30),
            onSecondaryContainer = secondary(90),
            tertiary = tertiary(80),
            onTertiary = tertiary(20),
            background = neutralVariant6,
            onBackground = neutral(90),
            surface = neutralVariant6,
            onSurface = neutral(90),
            surfaceVariant = neutralVariant(30),
            onSurfaceVariant = neutralVariant(80),
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            outline = neutralVariant(60),
            outlineVariant = neutralVariant(30),
            scrim = neutral(0),

            surfaceBright = neutralVariant24,
            surfaceDim = neutralVariant6,
            surfaceContainerHighest = neutralVariant22,
            surfaceContainerHigh = neutralVariant17,
            surfaceContainer = neutralVariant12,
            surfaceContainerLow = neutralVariant(10),
            surfaceContainerLowest = neutralVariant4,
            surfaceTint = primary(80),
        )
    } else {
        lightColorScheme(
            primary = primary(40),
            onPrimary = primary(100),
            primaryContainer = primary(90),
            onPrimaryContainer = primary(10),
            inversePrimary = primary(80),
            secondary = secondary(40),
            onSecondary = secondary(100),
            secondaryContainer = secondary(90),
            onSecondaryContainer = secondary(10),
            tertiary = tertiary(40),
            onTertiary = tertiary(100),
            tertiaryContainer = tertiary(90),
            onTertiaryContainer = tertiary(10),
            background = neutralVariant98,
            onBackground = neutralVariant(10),
            surface = neutralVariant98,
            onSurface = neutralVariant(10),
            surfaceVariant = neutralVariant(90),
            onSurfaceVariant = neutralVariant(30),
            inverseSurface = neutral(20),
            inverseOnSurface = neutral(95),
            outline = neutralVariant(50),
            outlineVariant = neutralVariant(80),
            scrim = neutral(0),

            surfaceBright = neutralVariant98,
            surfaceDim = neutralVariant87,
            surfaceContainerHighest = neutralVariant(90),
            surfaceContainerHigh = neutralVariant92,
            surfaceContainer = neutralVariant94,
            surfaceContainerLow = neutralVariant96,
            surfaceContainerLowest = neutralVariant(100),
            surfaceTint = primary(40),
        )
    }
}
