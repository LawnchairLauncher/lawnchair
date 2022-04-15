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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.state
import app.lawnchair.search.LawnchairSearchAlgorithm
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.ifNotNull
import app.lawnchair.util.rememberGridOption
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.state

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

@ExperimentalAnimationApi
fun NavGraphBuilder.appDrawerGraph(route: String) {
    preferenceGraph(route, { AppDrawerPreferences() }) { subRoute ->
        hiddenAppsGraph(route = subRoute(AppDrawerRoutes.HIDDEN_APPS))
    }
}

interface AppDrawerPreferenceCollectorScope : PreferenceCollectorScope {
    val hiddenApps: Set<String>
    val hideAppDrawerSearchBar: Boolean
    val autoShowKeyboardInDrawer: Boolean
    val drawerIconSizeFactor: Float
    val showIconLabelsInDrawer: Boolean
    val drawerIconLabelSizeFactor: Float
    val drawerCellHeightFactor: Float
    val enableFuzzySearch: Boolean
    val drawerColumns: Int
}

@Composable
fun AppDrawerPreferenceCollector(content: @Composable AppDrawerPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val gridOption = rememberGridOption()
    val hiddenApps by preferenceManager.hiddenApps.state()
    val hideAppDrawerSearchBar by preferenceManager.hideAppDrawerSearchBar.state()
    val autoShowKeyboardInDrawer by preferenceManager.autoShowKeyboardInDrawer.state()
    val drawerIconSizeFactor by preferenceManager.drawerIconSizeFactor.state()
    val showIconLabelsInDrawer by preferenceManager.showIconLabelsInDrawer.state()
    val drawerIconLabelSizeFactor by preferenceManager.drawerIconLabelSizeFactor.state()
    val drawerCellHeightFactor by preferenceManager.drawerCellHeightFactor.state()
    val enableFuzzySearch by preferenceManager.enableFuzzySearch.state()
    val drawerColumns by preferenceManager.drawerColumns.state(gridOption = gridOption)
    ifNotNull(
        hiddenApps, hideAppDrawerSearchBar,
        autoShowKeyboardInDrawer, drawerIconSizeFactor,
        showIconLabelsInDrawer, drawerIconLabelSizeFactor,
        drawerCellHeightFactor, enableFuzzySearch,
        drawerColumns,
    ) {
        object : AppDrawerPreferenceCollectorScope {
            override val hiddenApps = it[0] as Set<String>
            override val hideAppDrawerSearchBar = it[1] as Boolean
            override val autoShowKeyboardInDrawer = it[2] as Boolean
            override val drawerIconSizeFactor = it[3] as Float
            override val showIconLabelsInDrawer = it[4] as Boolean
            override val drawerIconLabelSizeFactor = it[5] as Float
            override val drawerCellHeightFactor = it[6] as Float
            override val enableFuzzySearch = it[7] as Boolean
            override val drawerColumns = it[8] as Int
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@ExperimentalAnimationApi
@Composable
fun AppDrawerPreferences() {
    AppDrawerPreferenceCollector {
        val prefs = preferenceManager()
        val resources = LocalContext.current.resources
        PreferenceLayout(label = stringResource(id = R.string.app_drawer_label)) {
            PreferenceGroup(heading = stringResource(id = R.string.general_label), isFirstChild = true) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.hidden_apps_label),
                    subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenApps.size, hiddenApps.size),
                    destination = subRoute(name = AppDrawerRoutes.HIDDEN_APPS),
                )
                SliderPreference(
                    label = stringResource(id = R.string.background_opacity),
                    adapter = prefs.drawerOpacity.getAdapter(),
                    step = 0.1f,
                    valueRange = 0F..1F,
                    showAsPercentage = true,
                )
                SuggestionsPreference()
            }
            val deviceSearchEnabled = LawnchairSearchAlgorithm.isDeviceSearchEnabled(LocalContext.current)
            PreferenceGroup(heading = stringResource(id = R.string.pref_category_search)) {
                SwitchPreference2(
                    label = stringResource(id = R.string.show_app_search_bar),
                    checked = !hideAppDrawerSearchBar,
                    edit = { hideAppDrawerSearchBar.set(value = !it) },
                )
                AnimatedVisibility(
                    visible = !hideAppDrawerSearchBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    DividerColumn {
                        SwitchPreference2(
                            checked = autoShowKeyboardInDrawer,
                            edit = { autoShowKeyboardInDrawer.set(value = it) },
                            label = stringResource(id = R.string.pref_search_auto_show_keyboard),
                        )
                        if (!deviceSearchEnabled) {
                            SwitchPreference2(
                                checked = enableFuzzySearch,
                                edit = { enableFuzzySearch.set(value = it) },
                                label = stringResource(id = R.string.fuzzy_search_title),
                                description = stringResource(id = R.string.fuzzy_search_desc)
                            )
                        }
                    }
                }
            }
            if (deviceSearchEnabled) {
                AnimatedVisibility(
                    visible = !hideAppDrawerSearchBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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
                SliderPreference2(
                    label = stringResource(id = R.string.app_drawer_columns),
                    value = drawerColumns,
                    edit = { drawerColumns.set(value = it) },
                    step = 1,
                    valueRange = 3..10,
                )
                SliderPreference2(
                    value = drawerCellHeightFactor,
                    edit = { drawerCellHeightFactor.set(value = it) },
                    label = stringResource(id = R.string.row_height_label),
                    valueRange = 0.7F..1.5F,
                    step = 0.1F,
                    showAsPercentage = true
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SliderPreference2(
                    label = stringResource(id = R.string.icon_size),
                    value = drawerIconSizeFactor,
                    edit = { drawerIconSizeFactor.set(value = it) },
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
                SwitchPreference2(
                    checked = showIconLabelsInDrawer,
                    edit = { showIconLabelsInDrawer.set(value = it) },
                    label = stringResource(id = R.string.show_home_labels),
                )
                AnimatedVisibility(
                    visible = showIconLabelsInDrawer,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SliderPreference2(
                        label = stringResource(id = R.string.label_size),
                        value = drawerIconLabelSizeFactor,
                        edit = { drawerIconLabelSizeFactor.set(value = it) },
                        step = 0.1F,
                        valueRange = 0.5F..1.5F,
                        showAsPercentage = true,
                    )
                }
            }
        }
    }
}
