package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.runtime.Composable
import app.lawnchair.ui.theme.lightenColor

open class ColorPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)
