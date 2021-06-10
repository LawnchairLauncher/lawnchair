package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
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
    PreferenceLayout {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = preferenceManager().clearAllAsAction.getAdapter(),
                label = stringResource(id = R.string.clear_all_as_action_label),
                showDivider = false
            )
        }
    }
}
