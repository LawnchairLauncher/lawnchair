package app.lawnchair.theme.utils

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.lawnchair.theme.colorscheme.LightDarkScheme
import com.google.android.material.color.utilities.hct.Hct
import com.google.android.material.color.utilities.scheme.SchemeVibrant

fun getM3ColorScheme(
    accentColor: Hct?,
    darkTheme: Boolean,
) = SchemeVibrant(
    accentColor,
    darkTheme,
    3.0,
)

fun getM3ComposeColorScheme(
    accentColor: Hct?,
    darkTheme: Boolean
): ColorScheme {
    val scheme = getM3ColorScheme(accentColor, darkTheme)
    return scheme.let {
        ColorScheme(
            Color(it.primary),
            Color(it.onPrimary),
            Color(it.primaryContainer),
            Color(it.onPrimaryContainer),
            Color(it.inversePrimary),
            Color(it.secondary),
            Color(it.onSecondary),
            Color(it.secondaryContainer),
            Color(it.onSecondaryContainer),
            Color(it.tertiary),
            Color(it.onTertiary),
            Color(it.tertiaryContainer),
            Color(it.onTertiaryContainer),
            Color(it.background),
            Color(it.onBackground),
            Color(it.surface),
            Color(it.onSurface),
            Color(it.surfaceVariant),
            Color(it.onSurfaceVariant),
            Color(it.surfaceTint),
            Color(it.inverseSurface),
            Color(it.inverseOnSurface),
            Color(it.error),
            Color(it.onError),
            Color(it.errorContainer),
            Color(it.onErrorContainer),
            Color(it.outline),
            Color(it.outlineVariant),
            Color(it.scrim),
            Color(it.surfaceBright),
            Color(it.surfaceDim),
            Color(it.surfaceContainer),
            Color(it.surfaceContainerHigh),
            Color(it.surfaceContainerHighest),
            Color(it.surfaceContainerLow),
            Color(it.surfaceContainerLowest),
        )
    }
}

fun getLightDarkScheme(accentColor: Int): LightDarkScheme {
    val hct = Hct.fromInt(accentColor)

    return LightDarkScheme(
        getM3ComposeColorScheme(hct, false),
        getM3ComposeColorScheme(hct, true)
    )
}

@RequiresApi(Build.VERSION_CODES.S)
fun getSystemLightDarkScheme(context: Context): LightDarkScheme {
    return LightDarkScheme(
        dynamicLightColorScheme(context),
        dynamicDarkColorScheme(context),
    )
}
