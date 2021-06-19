package dev.kdrag0n.android12ext.monet.colors

import androidx.compose.ui.graphics.Color

interface Color {
    // All colors should have a conversion path to linear sRGB
    fun toLinearSrgb(): LinearSrgb
    fun toSrgb(): Srgb {
        return toLinearSrgb().toSrgb()
    }

    fun toComposeColor(): Color {
        return Color(toSrgb().quantize8())
    }
}
