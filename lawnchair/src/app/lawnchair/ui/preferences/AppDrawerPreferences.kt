/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.R

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

@ExperimentalAnimationApi
fun NavGraphBuilder.appDrawerGraph(route: String) {
    preferenceGraph(route, { AppDrawerPreferences() }) { subRoute ->
        hiddenAppsGraph(route = subRoute(AppDrawerRoutes.HIDDEN_APPS))
    }
}

@ExperimentalAnimationApi
@Composable
fun AppDrawerPreferences() {
    val prefs = preferenceManager()
    val resources = LocalContext.current.resources
    PreferenceLayout(label = stringResource(id = R.string.app_drawer_label)) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label), isFirstChild = true) {
            NavigationActionPreference(
                label = stringResource(id = R.string.hidden_apps_label),
                subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenAppsCount(), hiddenAppsCount()),
                destination = subRoute(name = AppDrawerRoutes.HIDDEN_APPS),
                showDivider = false
            )
            SwitchPreference(
                adapter = prefs.useFuzzySearch.getAdapter(),
                label = stringResource(id = R.string.fuzzy_search_title),
                description = stringResource(id = R.string.fuzzy_search_desc)
            )
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = prefs.drawerOpacity.getAdapter(),
                step = 0.1f,
                valueRange = 0.7F..1F,
                showAsPercentage = true,
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.app_drawer_columns),
                adapter = prefs.allAppsColumns.getAdapter(),
                step = 1,
                valueRange = 3..10,
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                adapter = prefs.allAppsIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
                showDivider = false
            )
            val allAppsIconLabels = prefs.allAppsIconLabels.getAdapter()
            SwitchPreference(
                allAppsIconLabels,
                label = stringResource(id = R.string.show_home_labels),
            )
            AnimatedVisibility(
                visible = allAppsIconLabels.state.value,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs.allAppsTextSizeFactor.getAdapter(),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
    }
}
