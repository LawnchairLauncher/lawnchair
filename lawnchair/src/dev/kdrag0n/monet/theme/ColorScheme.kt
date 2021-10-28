package dev.kdrag0n.monet.theme

import app.lawnchair.theme.toAndroidColor
import dev.kdrag0n.colorkt.Color
import androidx.compose.ui.graphics.Color as ComposeColor

typealias ColorSwatch = Map<Int, Color>

abstract class ColorScheme {
    abstract val neutral1: ColorSwatch
    abstract val neutral2: ColorSwatch

    abstract val accent1: ColorSwatch
    abstract val accent2: ColorSwatch
    abstract val accent3: ColorSwatch

    fun neutral(tonal: Int) = resolveComposeColor(neutral1, tonal)
    fun neutralVariant(tonal: Int) = resolveComposeColor(neutral2, tonal)

    fun primary(tonal: Int) = resolveComposeColor(accent1, tonal)
    fun secondary(tonal: Int) = resolveComposeColor(accent2, tonal)
    fun tertiary(tonal: Int) = resolveComposeColor(accent3, tonal)

    private fun resolveComposeColor(swatch: ColorSwatch, tonal: Int): ComposeColor {
        val lum = 1000 - 10 * tonal
        return swatch[lum]!!.toComposeColor()
    }

    // Helpers
    val neutralColors: List<ColorSwatch>
        get() = listOf(neutral1, neutral2)
    val accentColors: List<ColorSwatch>
        get() = listOf(accent1, accent2, accent3)
}

private fun Color.toComposeColor() = ComposeColor(toAndroidColor())
