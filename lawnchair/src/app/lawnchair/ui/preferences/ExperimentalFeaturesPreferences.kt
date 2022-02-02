package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.font.FontCache
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SwitchPreference
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
fun NavGraphBuilder.experimentalFeaturesGraph(route: String) {
    preferenceGraph(route, { ExperimentalFeaturesPreferences() })
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun ExperimentalFeaturesPreferences() {
    val preferenceManager = preferenceManager()
    val context = LocalContext.current

    PreferenceLayout(label = stringResource(id = R.string.experimental_features_label)) {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = preferenceManager.enableFontSelection.getAdapter(),
                label = stringResource(id = R.string.font_picker_label),
                description = stringResource(id = R.string.font_picker_description),
                onChange = {
                    if (!it) {
                        val fontCache = FontCache.INSTANCE.get(context)
                        preferenceManager.workspaceFont.set(newValue = fontCache.uiText)
                    }
                }
            )
            SwitchPreference(
                adapter = preferenceManager.enableIconSelection.getAdapter(),
                label = stringResource(id = R.string.icon_picker_label),
                description = stringResource(id = R.string.icon_picker_description),
                onChange = {
                    if (!it) {
                        val iconOverrideRepository = IconOverrideRepository.INSTANCE.get(context)

                        CoroutineScope(Dispatchers.IO).launch {
                            iconOverrideRepository.deleteAll()
                        }
                    }
                }
            )
        }
    }
}
