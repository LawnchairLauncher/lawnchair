package app.lawnchair.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun LightDarkScheme.toM3ColorScheme(isDark: Boolean): ColorScheme = remember(this, isDark) {
    if (isDark) {
        this.darkScheme.let {
            ColorScheme(
                primary = Color(it.primary),
                onPrimary = Color(it.onPrimary),
                primaryContainer = Color(it.primaryContainer),
                onPrimaryContainer = Color(it.onPrimaryContainer),
                inversePrimary = Color(it.inversePrimary),
                secondary = Color(it.secondary),
                onSecondary = Color(it.onSecondary),
                secondaryContainer = Color(it.secondaryContainer),
                onSecondaryContainer = Color(it.onSecondaryContainer),
                tertiary = Color(it.tertiary),
                onTertiary = Color(it.onTertiary),
                tertiaryContainer = Color(it.tertiaryContainer),
                onTertiaryContainer = Color(it.onTertiaryContainer),
                background = Color(it.background),
                onBackground = Color(it.onBackground),
                surface = Color(it.surface),
                onSurface = Color(it.onSurface),
                surfaceVariant = Color(it.surfaceVariant),
                onSurfaceVariant = Color(it.onSurfaceVariant),
                surfaceTint = Color(it.surfaceTint),
                inverseSurface = Color(it.inverseSurface),
                inverseOnSurface = Color(it.inverseOnSurface),
                error = Color(it.error),
                onError = Color(it.onError),
                errorContainer = Color(it.errorContainer),
                onErrorContainer = Color(it.onErrorContainer),
                outline = Color(it.outline),
                outlineVariant = Color(it.outlineVariant),
                scrim = Color(it.scrim),
                surfaceBright = Color(it.surfaceBright),
                surfaceDim = Color(it.surfaceDim),
                surfaceContainer = Color(it.surfaceContainer),
                surfaceContainerHigh = Color(it.surfaceContainerHigh),
                surfaceContainerHighest = Color(it.surfaceContainerHighest),
                surfaceContainerLow = Color(it.surfaceContainerLow),
                surfaceContainerLowest = Color(it.surfaceContainerLowest),
            )
        }
    } else {
        this.lightScheme.let {
            ColorScheme(
                primary = Color(it.primary),
                onPrimary = Color(it.onPrimary),
                primaryContainer = Color(it.primaryContainer),
                onPrimaryContainer = Color(it.onPrimaryContainer),
                inversePrimary = Color(it.inversePrimary),
                secondary = Color(it.secondary),
                onSecondary = Color(it.onSecondary),
                secondaryContainer = Color(it.secondaryContainer),
                onSecondaryContainer = Color(it.onSecondaryContainer),
                tertiary = Color(it.tertiary),
                onTertiary = Color(it.onTertiary),
                tertiaryContainer = Color(it.tertiaryContainer),
                onTertiaryContainer = Color(it.onTertiaryContainer),
                background = Color(it.background),
                onBackground = Color(it.onBackground),
                surface = Color(it.surface),
                onSurface = Color(it.onSurface),
                surfaceVariant = Color(it.surfaceVariant),
                onSurfaceVariant = Color(it.onSurfaceVariant),
                surfaceTint = Color(it.surfaceTint),
                inverseSurface = Color(it.inverseSurface),
                inverseOnSurface = Color(it.inverseOnSurface),
                error = Color(it.error),
                onError = Color(it.onError),
                errorContainer = Color(it.errorContainer),
                onErrorContainer = Color(it.onErrorContainer),
                outline = Color(it.outline),
                outlineVariant = Color(it.outlineVariant),
                scrim = Color(it.scrim),
                surfaceBright = Color(it.surfaceBright),
                surfaceDim = Color(it.surfaceDim),
                surfaceContainer = Color(it.surfaceContainer),
                surfaceContainerHigh = Color(it.surfaceContainerHigh),
                surfaceContainerHighest = Color(it.surfaceContainerHighest),
                surfaceContainerLow = Color(it.surfaceContainerLow),
                surfaceContainerLowest = Color(it.surfaceContainerLowest),
            )
        }
    }
}
