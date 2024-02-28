package app.lawnchair.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ln

@Composable
fun dev.kdrag0n.monet.theme.ColorScheme.toM3ColorScheme(isDark: Boolean): ColorScheme = remember(this, isDark) {
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
            tertiaryContainer = tertiary(30),
            onTertiaryContainer = tertiary(90),
            background = neutral(10),
            onBackground = neutral(90),
            surface = neutral(10),
            onSurface = neutral(90),
            surfaceVariant = neutralVariant(30),
            onSurfaceVariant = neutralVariant(80),
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            outline = neutralVariant(60),
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
            tertiaryContainer = tertiary(90),
            onTertiaryContainer = tertiary(10),
            background = neutral(99),
            onBackground = neutral(10),
            surface = neutral(99),
            onSurface = neutral(10),
            surfaceVariant = neutralVariant(90),
            onSurfaceVariant = neutralVariant(30),
            inverseSurface = neutral(20),
            inverseOnSurface = neutral(95),
            outline = neutralVariant(50),
        )
    }
}

internal fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return primary.copy(alpha = alpha).compositeOver(surface)
}
