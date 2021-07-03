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
    0xFFF32020,
    0xFFF20D69,
    0xFF7452FF,
    0xFF2C41C9,
    LAWNCHAIR_BLUE,
    0xFF00BAD6,
    0xFF00A399,
    0xFF47B84F,
    0xFFFFBB00,
    0xFFFF9800,
    0xFF7C5445,
    0xFF67818E
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
