package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import com.android.launcher3.R
import com.android.launcher3.Utilities

object ThemeChoice {
    val light = "light"
    val dark = "dark"
    val system = "system"
}

val themeEntries = listOf(
    ListPreferenceEntry(ThemeChoice.light) { stringResource(id = R.string.theme_light) },
    ListPreferenceEntry(ThemeChoice.dark) { stringResource(id = R.string.theme_dark) },
    ListPreferenceEntry(ThemeChoice.system) {
        when {
            Utilities.ATLEAST_Q -> stringResource(id = R.string.theme_system_default)
            else -> stringResource(id = R.string.theme_follow_wallpaper)
        }
    },
)

@ExperimentalMaterialApi
@Composable
fun ThemePreference(
    showDivider: Boolean = true
) {
    ListPreference(
        adapter = preferenceManager().launcherTheme.getAdapter(),
        entries = themeEntries,
        label = stringResource(id = R.string.theme_label),
        showDivider = showDivider
    )
}
