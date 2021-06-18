package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.theme.getDefaultAccentColor
import com.android.launcher3.R

val accentColorEntries = listOf(
    ColorPreferenceEntry(
        value = 0,
        lightColor = { LocalContext.current.getDefaultAccentColor(false) },
        darkColor = { LocalContext.current.getDefaultAccentColor(true) }
    ),
    AccentColorEntry(value = 0xFFF44336),
    AccentColorEntry(value = 0xFF673AB7),
    AccentColorEntry(value = 0xFF2196F3),
    AccentColorEntry(value = 0xFF4CAF50),
    AccentColorEntry(value = 0xFFFF9800),
)

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun AccentColorPreference(showDivider: Boolean) {
    ColorPreference(
        adapter = preferenceManager().accentColor.getAdapter(),
        label = stringResource(id = R.string.accent_color),
        entries = accentColorEntries,
        showDivider = showDivider
    )
}

class AccentColorEntry(value: Long) : ColorPreferenceEntry<Int>(value.toInt(), { value.toInt() })
