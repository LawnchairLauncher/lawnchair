package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import androidx.compose.runtime.Composable
import app.lawnchair.ui.theme.lightenColor

class ColorPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: (Context) -> Int,
    val darkColor: (Context) -> Int = { context -> lightenColor(lightColor(context)) },
)
