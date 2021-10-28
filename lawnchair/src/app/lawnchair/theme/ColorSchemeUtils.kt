package app.lawnchair.theme

import androidx.compose.material.Colors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme as M3ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kdrag0n.monet.theme.ColorScheme
import kotlin.math.ln

@Composable
fun m3ColorScheme(colorScheme: ColorScheme, isDark: Boolean) = remember(colorScheme, isDark) {
    when (isDark) {
        false -> lightColorScheme(
            primary = colorScheme.primary(40),
            onPrimary = colorScheme.primary(100),
            primaryContainer = colorScheme.primary(90),
            onPrimaryContainer = colorScheme.primary(10),
            inversePrimary = colorScheme.primary(80),
            secondary = colorScheme.secondary(40),
            onSecondary = colorScheme.secondary(100),
            secondaryContainer = colorScheme.secondary(90),
            onSecondaryContainer = colorScheme.secondary(10),
            tertiaryContainer = colorScheme.tertiary(90),
            onTertiaryContainer = colorScheme.tertiary(10),
            background = colorScheme.neutral(99),
            onBackground = colorScheme.neutral(10),
            surface = colorScheme.neutral(99),
            onSurface = colorScheme.neutral(10),
            surfaceVariant = colorScheme.neutralVariant(90),
            onSurfaceVariant = colorScheme.neutralVariant(30),
            inverseSurface = colorScheme.neutral(20),
            inverseOnSurface = colorScheme.neutral(95),
            outline = colorScheme.neutralVariant(50),
        )
        true -> darkColorScheme(
            primary = colorScheme.primary(80),
            onPrimary = colorScheme.primary(20),
            primaryContainer = colorScheme.primary(30),
            onPrimaryContainer = colorScheme.primary(90),
            inversePrimary = colorScheme.primary(40),
            secondary = colorScheme.secondary(80),
            onSecondary = colorScheme.secondary(20),
            secondaryContainer = colorScheme.secondary(30),
            onSecondaryContainer = colorScheme.secondary(90),
            tertiaryContainer = colorScheme.tertiary(30),
            onTertiaryContainer = colorScheme.tertiary(90),
            background = colorScheme.neutral(10),
            onBackground = colorScheme.neutral(90),
            surface = colorScheme.neutral(10),
            onSurface = colorScheme.neutral(90),
            surfaceVariant = colorScheme.neutralVariant(30),
            onSurfaceVariant = colorScheme.neutralVariant(80),
            inverseSurface = colorScheme.neutral(90),
            inverseOnSurface = colorScheme.neutral(20),
            outline = colorScheme.neutralVariant(60),
        )
    }
}

internal fun M3ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return primary.copy(alpha = alpha).compositeOver(surface)
}

@Composable
fun materialColors(colorScheme: M3ColorScheme, isDark: Boolean) = remember(colorScheme, isDark) {
    Colors(
        primary = colorScheme.primary,
        primaryVariant = colorScheme.primaryContainer,
        secondary = colorScheme.primary,
        secondaryVariant = colorScheme.primaryContainer,
        background = colorScheme.background,
        surface = colorScheme.surfaceColorAtElevation(1.dp),
        error = colorScheme.error,
        onPrimary = colorScheme.onPrimary,
        onSecondary = colorScheme.onSecondary,
        onBackground = colorScheme.onBackground,
        onSurface = colorScheme.onSurface,
        onError = colorScheme.onError,
        isLight = !isDark
    )
}
