package app.lawnchair.theme.color

import android.content.Context
import app.lawnchair.theme.color.tokens.ColorTokens

fun generateColor(context: Context): Int {
    val accentColors = listOf(
        ColorTokens.Accent1_50,
        ColorTokens.Accent1_100,
        ColorTokens.Accent1_200,
        ColorTokens.Accent1_300,
        ColorTokens.Accent1_500,
        ColorTokens.Accent1_600,
        ColorTokens.Accent2_50,
        ColorTokens.Accent2_100,
        ColorTokens.Accent2_500,
        ColorTokens.Accent2_600,
        ColorTokens.Accent3_50,
        ColorTokens.Accent3_100,
    )
    val randomAccent = accentColors.random()
    return randomAccent.resolveColor(context)
}
