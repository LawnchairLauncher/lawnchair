package app.lawnchair.ui.preferences

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.quickstepGraph(route: String) {
    preferenceGraph(route, { QuickstepPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun QuickstepPreferences() {
    pageMeta.provide(Meta(title = stringResource(id = R.string.quickstep_label)))
    val prefs = preferenceManager()
    PreferenceLayout {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = prefs.clearAllAsAction.getAdapter(),
                label = stringResource(id = R.string.clear_all_as_action_label),
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.window_corner_radius_label)) {
            val overrideWindowCornerRadius by prefs.overrideWindowCornerRadius.observeAsState()
            SwitchPreference(
                adapter = prefs.overrideWindowCornerRadius.getAdapter(),
                label = stringResource(id = R.string.override_window_corner_radius_label),
                description = stringResource(id = R.string.override_window_corner_radius_description),
                showDivider = overrideWindowCornerRadius
            )
            AnimatedVisibility(
                visible = overrideWindowCornerRadius,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.window_corner_radius_label),
                    adapter = prefs.windowCornerRadius.getAdapter(),
                    steps = 0,
                    valueRange = 80f..150f,
                    showDivider = false
                )
            }
        }
    }
}
