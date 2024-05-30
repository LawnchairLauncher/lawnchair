package app.lawnchair.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.lawnchair.theme.colorscheme.LightDarkScheme

@Composable
fun LightDarkScheme.toM3ColorScheme(isDark: Boolean): ColorScheme = remember(this, isDark) {
    if (isDark) {
        this.darkScheme
    } else {
        this.lightScheme
    }
}
