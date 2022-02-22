package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.preferences.components.SwitchPreference2
import app.lawnchair.util.ifNotNull
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.state
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
fun NavGraphBuilder.experimentalFeaturesGraph(route: String) {
    preferenceGraph(route, { ExperimentalFeaturesPreferences() })
}

interface ExperimentalPreferenceCollectorScope : PreferenceCollectorScope {
    val enableFontSelection: Boolean
}

@Composable
fun ExperimentalPreferenceCollector(content: @Composable ExperimentalPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val enableFontSelection by preferenceManager.enableFontSelection.state()
    ifNotNull(enableFontSelection) {
        object : ExperimentalPreferenceCollectorScope {
            override val enableFontSelection = it[0] as Boolean
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun ExperimentalFeaturesPreferences() {
    val preferenceManager = preferenceManager()
    val context = LocalContext.current

    ExperimentalPreferenceCollector {
        PreferenceLayout(label = stringResource(id = R.string.experimental_features_label)) {
            PreferenceGroup(isFirstChild = true) {
                SwitchPreference2(
                    checked = enableFontSelection,
                    label = stringResource(id = R.string.font_picker_label),
                    description = stringResource(id = R.string.font_picker_description),
                    edit = { enableFontSelection.set(value = it) },
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
}
