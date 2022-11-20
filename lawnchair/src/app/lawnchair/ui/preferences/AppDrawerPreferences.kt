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
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.LawnchairSearchAlgorithm
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.R

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

fun NavGraphBuilder.appDrawerGraph(route: String) {
    preferenceGraph(route, { AppDrawerPreferences() }) { subRoute ->
        hiddenAppsGraph(route = subRoute(AppDrawerRoutes.HIDDEN_APPS))
    }
}

@Composable
fun AppDrawerPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val resources = LocalContext.current.resources
    PreferenceLayout(label = stringResource(id = R.string.app_drawer_label)) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = prefs.drawerOpacity.getAdapter(),
                step = 0.1f,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            SuggestionsPreference()
        }

        val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.hidden_apps_label)) {
            val hiddenApps = prefs2.hiddenApps.getAdapter().state.value
            val hideHiddenAppsInSearch = !prefs2.showHiddenAppsInSearch.getAdapter()
            val enableSmartHide = prefs2.enableSmartHide.getAdapter()
            NavigationActionPreference(
                label = stringResource(id = R.string.hidden_apps_label),
                subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenApps.size, hiddenApps.size),
                destination = subRoute(name = AppDrawerRoutes.HIDDEN_APPS),
            )
            ExpandAndShrink(visible = hiddenApps.isNotEmpty() && showDrawerSearchBar.state.value) {
                DividerColumn {
                    SwitchPreference(
                        label = stringResource(id = R.string.hide_hidden_apps_search),
                        adapter = hideHiddenAppsInSearch,
                    )
                    ExpandAndShrink(visible = hideHiddenAppsInSearch.state.value) {
                        SwitchPreference(
                            label = stringResource(id = R.string.show_enable_smart_hide),
                            adapter = enableSmartHide,
                        )
                    }
                }
            }
        }

        val deviceSearchEnabled = LawnchairSearchAlgorithm.isDeviceSearchEnabled(LocalContext.current)
        PreferenceGroup(heading = stringResource(id = R.string.pref_category_search)) {
            SwitchPreference(
                label = stringResource(id = R.string.show_app_search_bar),
                adapter = showDrawerSearchBar,
            )
            ExpandAndShrink(visible = showDrawerSearchBar.state.value) {
                DividerColumn {
                    SwitchPreference(
                        adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                        label = stringResource(id = R.string.pref_search_auto_show_keyboard),
                    )
                    if (!deviceSearchEnabled) {
                        SwitchPreference(
                            adapter = prefs2.enableFuzzySearch.getAdapter(),
                            label = stringResource(id = R.string.fuzzy_search_title),
                            description = stringResource(id = R.string.fuzzy_search_desc)
                        )
                    }
                }
            }
        }
        if (deviceSearchEnabled) {
            ExpandAndShrink(visible = showDrawerSearchBar.state.value) {
                PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
                    SwitchPreference(
                        adapter = prefs.searchResultShortcuts.getAdapter(),
                        label = stringResource(id = R.string.search_pref_result_shortcuts_title)
                    )
                    SwitchPreference(
                        adapter = prefs.searchResultPeople.getAdapter(),
                        label = stringResource(id = R.string.search_pref_result_people_title)
                    )
                    SwitchPreference(
                        adapter = prefs.searchResultPixelTips.getAdapter(),
                        label = stringResource(id = R.string.search_pref_result_tips_title)
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.app_drawer_columns),
                adapter = prefs2.drawerColumns.getAdapter(),
                step = 1,
                valueRange = 3..10,
            )
            SliderPreference(
                adapter = prefs2.drawerCellHeightFactor.getAdapter(),
                label = stringResource(id = R.string.row_height_label),
                valueRange = 0.7F..1.5F,
                step = 0.1F,
                showAsPercentage = true
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                adapter = prefs2.drawerIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            val showDrawerLabels = prefs2.showIconLabelsInDrawer.getAdapter()
            SwitchPreference(
                adapter = showDrawerLabels,
                label = stringResource(id = R.string.show_home_labels),
            )
            ExpandAndShrink(visible = showDrawerLabels.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.drawerIconLabelSizeFactor.getAdapter(),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
    }
}
