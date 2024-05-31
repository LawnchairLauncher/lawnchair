package app.lawnchair.theme.colorscheme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme as ComposeColorScheme
import androidx.compose.ui.graphics.Color
import app.lawnchair.theme.utils.dynamicDarkColorScheme
import app.lawnchair.theme.utils.dynamicLightColorScheme
import app.lawnchair.theme.utils.dynamicTonalPalette
import com.google.android.material.color.utilities.hct.Hct
import com.google.android.material.color.utilities.scheme.SchemeVibrant

fun getColorScheme(
    accentColor: Int,
    darkTheme: Boolean,
): ColorScheme {
    val hct = Hct.fromInt(accentColor)
    val scheme = SchemeVibrant(
        hct,
        darkTheme,
        0.0,
    )

    return ColorScheme(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        inversePrimary = scheme.inversePrimary,
        secondary = scheme.secondary,
        onSecondary = scheme.onSecondary,
        secondaryContainer = scheme.secondaryContainer,
        onSecondaryContainer = scheme.onSecondaryContainer,
        tertiary = scheme.tertiary,
        onTertiary = scheme.onTertiary,
        tertiaryContainer = scheme.tertiaryContainer,
        onTertiaryContainer = scheme.onTertiaryContainer,
        background = scheme.background,
        onBackground = scheme.onBackground,
        surface = scheme.surface,
        onSurface = scheme.onSurface,
        surfaceVariant = scheme.surfaceVariant,
        onSurfaceVariant = scheme.onSurfaceVariant,
        surfaceTint = scheme.surfaceTint,
        inverseSurface = scheme.inverseSurface,
        inverseOnSurface = scheme.inverseOnSurface,
        error = scheme.error,
        onError = scheme.onError,
        errorContainer = scheme.errorContainer,
        onErrorContainer = scheme.onErrorContainer,
        outline = scheme.outline,
        outlineVariant = scheme.outlineVariant,
        scrim = scheme.scrim,
        surfaceBright = scheme.surfaceBright,
        surfaceDim = scheme.surfaceDim,
        surfaceContainer = scheme.surfaceContainer,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        surfaceContainerHighest = scheme.surfaceContainerHighest,
        surfaceContainerLow = scheme.surfaceContainerLow,
        surfaceContainerLowest = scheme.surfaceContainerLowest,
    )
}
fun getLightDarkScheme(accentColor: Int): LightDarkScheme {
    return LightDarkScheme(
        getColorScheme(accentColor, false),
        getColorScheme(accentColor, true)
    )
}

@RequiresApi(Build.VERSION_CODES.S)
fun getSystemLightDarkScheme(context: Context): LightDarkScheme {
    return LightDarkScheme(
        dynamicLightColorScheme(context),
        dynamicDarkColorScheme(context),
    )
}


@RequiresApi(Build.VERSION_CODES.S)
internal fun dynamicLightColorScheme(context: Context): ColorScheme {
    val tonalPalette = dynamicTonalPalette(context)
    return dynamicLightColorScheme(tonalPalette)
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun dynamicDarkColorScheme(context: Context): ColorScheme {
    val tonalPalette = dynamicTonalPalette(context)
    return dynamicDarkColorScheme(tonalPalette)
}
