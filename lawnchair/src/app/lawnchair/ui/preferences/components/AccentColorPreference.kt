package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.theme.LAWNCHAIR_BLUE
import com.android.launcher3.R

val presetColors = listOf(
    0xFFF44336,
    0xFFE91E63,
    0xFF673AB7,
    0xFF3F51B5,
    LAWNCHAIR_BLUE,
    0xFF00BCD4,
    0xFF009688,
    0xFF4CAF50,
    0xFFFFC107,
    0xFFFF9800,
    0xFF795548,
    0xFF607D8B
)

val presets = presetColors.map {
    ColorPreferencePreset(it.toInt(), { it.toInt() })
}

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun AccentColorPreference(showDivider: Boolean) {
    ColorPreference(
        previewColor = MaterialTheme.colors.primary.toArgb(),
        customColorAdapter = preferenceManager().accentColor.getAdapter(),
        label = stringResource(id = R.string.accent_color),
        presets = presets,
        showDivider = showDivider,
        useSystemAccentAdapter = preferenceManager().useSystemAccent.getAdapter()
    )
}
