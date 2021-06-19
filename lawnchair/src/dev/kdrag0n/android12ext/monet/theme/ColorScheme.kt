package dev.kdrag0n.android12ext.monet.theme

import dev.kdrag0n.android12ext.monet.colors.Color

abstract class ColorScheme {
    abstract val neutral1: Map<Int, Color>
    abstract val neutral2: Map<Int, Color>

    abstract val accent1: Map<Int, Color>
    abstract val accent2: Map<Int, Color>
    abstract val accent3: Map<Int, Color>

    // Helpers
    val neutralColors: List<Map<Int, Color>>
        get() = listOf(neutral1, neutral2)
    val accentColors: List<Map<Int, Color>>
        get() = listOf(accent1, accent2, accent3)
}
