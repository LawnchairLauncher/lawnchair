package app.lawnchair.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlinx.collections.immutable.toPersistentList

object ThemeChoice {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}

val themeEntries = sequenceOf(
    ListPreferenceEntry(ThemeChoice.LIGHT) { stringResource(id = R.string.theme_light) },
    ListPreferenceEntry(ThemeChoice.DARK) { stringResource(id = R.string.theme_dark) },
    ListPreferenceEntry(ThemeChoice.SYSTEM) {
        stringResource(id = if (Utilities.ATLEAST_P) R.string.theme_system_default else R.string.theme_follow_wallpaper)
    },
)
    .filter {
        when (it.value) {
            ThemeChoice.SYSTEM -> Utilities.ATLEAST_O_MR1
            else -> true
        }
    }.toPersistentList()

@Composable
fun ThemePreference() {
    ListPreference(
        adapter = preferenceManager().launcherTheme.getAdapter(),
        entries = themeEntries,
        label = stringResource(id = R.string.theme_label),
    )
}
