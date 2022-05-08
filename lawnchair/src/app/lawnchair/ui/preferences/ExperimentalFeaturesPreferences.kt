package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SwitchPreference
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.experimentalFeaturesGraph(route: String) {
    preferenceGraph(route, { ExperimentalFeaturesPreferences() })
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun ExperimentalFeaturesPreferences() {
    val prefs = preferenceManager2()
    PreferenceLayout(label = stringResource(id = R.string.experimental_features_label)) {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = prefs.enableFontSelection.getAdapter(),
                label = stringResource(id = R.string.font_picker_label),
                description = stringResource(id = R.string.font_picker_description),
            )
            SwitchPreference(
                adapter = prefs.enableIconSelection.getAdapter(),
                label = stringResource(id = R.string.icon_picker_label),
                description = stringResource(id = R.string.icon_picker_description),
            )
        }
    }
}
