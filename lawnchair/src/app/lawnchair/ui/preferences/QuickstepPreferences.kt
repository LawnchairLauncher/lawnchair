package app.lawnchair.ui.preferences

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.util.isOnePlusStock
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.quickstepGraph(route: String) {
    preferenceGraph(route, { QuickstepPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun QuickstepPreferences() {
    val prefs = preferenceManager()
    val context = LocalContext.current
    val lensAvailable = remember {
        context.packageManager.getLaunchIntentForPackage("com.google.ar.lens") != null
    }

    PreferenceLayout(label = stringResource(id = R.string.quickstep_label)) {
        PreferenceGroup(
            heading = stringResource(id = R.string.recents_actions_label),
            isFirstChild = true
        ) {
            if (!isOnePlusStock) {
                SwitchPreference(
                    adapter = prefs.recentsActionScreenshot.getAdapter(),
                    label = stringResource(id = R.string.action_screenshot),
                )
            }
            SwitchPreference(
                adapter = prefs.recentsActionShare.getAdapter(),
                label = stringResource(id = R.string.action_share),
            )
            if (lensAvailable) {
                SwitchPreference(
                    adapter = prefs.recentsActionLens.getAdapter(),
                    label = stringResource(id = R.string.action_lens),
                )
            }
            SwitchPreference(
                adapter = prefs.recentsActionClearAll.getAdapter(),
                label = stringResource(id = R.string.recents_clear_all),
            )
        }
        val overrideWindowCornerRadius by prefs.overrideWindowCornerRadius.observeAsState()
        PreferenceGroup(
            heading = stringResource(id = R.string.window_corner_radius_label),
            description = stringResource(id = (R.string.window_corner_radius_description)),
            showDescription = overrideWindowCornerRadius
        ) {
            SwitchPreference(
                adapter = prefs.overrideWindowCornerRadius.getAdapter(),
                label = stringResource(id = R.string.override_window_corner_radius_label),
            )
            AnimatedVisibility(
                visible = overrideWindowCornerRadius,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.window_corner_radius_label),
                    adapter = prefs.windowCornerRadius.getAdapter(),
                    step = 0,
                    valueRange = 70..150
                )
            }
        }
    }
}
