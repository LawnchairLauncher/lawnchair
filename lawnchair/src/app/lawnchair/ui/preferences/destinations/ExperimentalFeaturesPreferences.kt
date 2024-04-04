package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.preferenceGraph
import com.android.launcher3.R

fun NavGraphBuilder.experimentalFeaturesGraph(route: String) {
    preferenceGraph(route, { ExperimentalFeaturesPreferences() })
}

@Composable
fun ExperimentalFeaturesPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.experimental_features_label),
        modifier = modifier,
    ) {
        PreferenceGroup {
            SwitchPreference(
                adapter = prefs2.enableFontSelection.getAdapter(),
                label = stringResource(id = R.string.font_picker_label),
                description = stringResource(id = R.string.font_picker_description),
            )
            SwitchPreference(
                adapter = prefs2.enableSmartspaceCalendarSelection.getAdapter(),
                label = stringResource(id = R.string.smartspace_calendar_label),
                description = stringResource(id = R.string.smartspace_calendar_description),
            )
            SwitchPreference(
                adapter = prefs.workspaceIncreaseMaxGridSize.getAdapter(),
                label = stringResource(id = R.string.workspace_increase_max_grid_size_label),
                description = stringResource(id = R.string.workspace_increase_max_grid_size_description),
            )
            SwitchPreference(
                adapter = prefs2.alwaysReloadIcons.getAdapter(),
                label = stringResource(id = R.string.always_reload_icons_label),
                description = stringResource(id = R.string.always_reload_icons_description),
            )
            SwitchPreference(
                adapter = prefs2.performWideSearch.getAdapter(),
                label = stringResource(id = R.string.perform_wide_search_title),
                description = stringResource(id = R.string.perform_wide_search_description),
            )
            SwitchPreference(
                adapter = prefs.recentsActionLocked.getAdapter(),
                label = stringResource(id = R.string.recents_lock_unlock),
                description = stringResource(id = R.string.recents_lock_unlock_description),
            )
        }
    }
}
