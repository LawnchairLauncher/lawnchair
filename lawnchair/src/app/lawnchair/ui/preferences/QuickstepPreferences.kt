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
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.util.isOnePlusStock
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities

fun NavGraphBuilder.quickstepGraph(route: String) {
    preferenceGraph(route, { QuickstepPreferences() })
}

@Composable
fun QuickstepPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val lensAvailable = remember {
        context.packageManager.getLaunchIntentForPackage("com.google.ar.lens") != null
    }

    PreferenceLayout(label = stringResource(id = R.string.quickstep_label)) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs.recentsTranslucentBackground.getAdapter(),
                label = stringResource(id = R.string.translucent_background),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.recents_actions_label)) {
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
            ExpandAndShrink(visible = overrideWindowCornerRadius) {
                SliderPreference(
                    label = stringResource(id = R.string.window_corner_radius_label),
                    adapter = prefs.windowCornerRadius.getAdapter(),
                    step = 0,
                    valueRange = 70..150
                )
            }
        }

        val idp = LauncherAppState.getIDP(LocalContext.current)
        if (Utilities.ATLEAST_S_V2 && idp.deviceType == InvariantDeviceProfile.TYPE_PHONE) {
            PreferenceGroup(
                heading = stringResource(id = R.string.taskbar_label)
            ) {
                SwitchPreference(
                    adapter = prefs2.enableTaskbarOnPhone.getAdapter(),
                    label = stringResource(id = R.string.enable_taskbar_experimental)
                )
            }
        }
    }
}
