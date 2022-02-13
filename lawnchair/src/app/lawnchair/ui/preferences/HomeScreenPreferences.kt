/*
 * Copyright 2022, Lawnchair
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
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.ifNotNull
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.patrykmichalik.preferencemanager.state

object HomeScreenRoutes {
    const val GRID = "grid"
}

@ExperimentalAnimationApi
fun NavGraphBuilder.homeScreenGraph(route: String) {
    preferenceGraph(route, { HomeScreenPreferences() }) { subRoute ->
        homeScreenGridGraph(route = subRoute(HomeScreenRoutes.GRID))
    }
}

interface HomeScreenPreferenceCollectorScope : PreferenceCollectorScope {
    val darkStatusBar: Boolean
    val roundedWidgets: Boolean
}

@Composable
fun HomeScreenPreferenceCollector(content: @Composable HomeScreenPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val darkStatusBar by preferenceManager.darkStatusBar.state()
    val roundedWidgets by preferenceManager.roundedWidgets.state()
    ifNotNull(darkStatusBar, roundedWidgets) {
        object : HomeScreenPreferenceCollectorScope {
            override val darkStatusBar = it[0] as Boolean
            override val roundedWidgets = it[1] as Boolean
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@ExperimentalAnimationApi
@Composable
fun HomeScreenPreferences() {
    val prefs = preferenceManager()
    HomeScreenPreferenceCollector {
        PreferenceLayout(label = stringResource(id = R.string.home_screen_label)) {
            PreferenceGroup(
                heading = stringResource(id = R.string.general_label),
                isFirstChild = true,
            ) {
                SwitchPreference(
                    prefs.addIconToHome.getAdapter(),
                    label = stringResource(id = R.string.auto_add_shortcuts_label),
                )
                SwitchPreference(
                    prefs.wallpaperScrolling.getAdapter(),
                    label = stringResource(id = R.string.wallpaper_scrolling_label),
                )
                SwitchPreference(
                    prefs.workspaceDt2s.getAdapter(),
                    label = stringResource(id = R.string.workspace_dt2s),
                )
                SwitchPreference2(
                    checked = darkStatusBar,
                    label = stringResource(id = R.string.dark_status_bar_label),
                    edit = { darkStatusBar.set(value = it) },
                )
                val columns by prefs.workspaceColumns.getAdapter()
                val rows by prefs.workspaceRows.getAdapter()
                NavigationActionPreference(
                    label = stringResource(id = R.string.home_screen_grid),
                    destination = subRoute(name = HomeScreenRoutes.GRID),
                    subtitle = stringResource(id = R.string.x_by_y, columns, rows),
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.what_to_show)) {
                val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
                SwitchPreference(
                    prefs.minusOneEnable.getAdapter(),
                    label = stringResource(id = R.string.minus_one_enable),
                    description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                    enabled = feedAvailable,
                )
                SwitchPreference(
                    prefs.smartSpaceEnable.getAdapter(),
                    label = stringResource(id = R.string.smart_space_enable),
                )
                SwitchPreference(
                    prefs.showStatusBar.getAdapter(),
                    label = stringResource(id = R.string.show_status_bar),
                )
                SwitchPreference(
                    prefs.showSysUiScrim.getAdapter(),
                    label = stringResource(id = R.string.show_sys_ui_scrim),
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SliderPreference(
                    label = stringResource(id = R.string.icon_size),
                    adapter = prefs.iconSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
                val showHomeLabels = prefs.showHomeLabels.getAdapter()
                SwitchPreference(
                    showHomeLabels,
                    label = stringResource(id = R.string.show_home_labels),
                )
                AnimatedVisibility(
                    visible = showHomeLabels.state.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SliderPreference(
                        label = stringResource(id = R.string.label_size),
                        adapter = prefs.textSizeFactor.getAdapter(),
                        step = 0.1f,
                        valueRange = 0.5F..1.5F,
                        showAsPercentage = true,
                    )
                }
            }
            if (!Utilities.ATLEAST_S) {
                PreferenceGroup(heading = stringResource(id = R.string.widget_button_text)) {
                    SwitchPreference2(
                        checked = roundedWidgets,
                        label = stringResource(id = R.string.force_rounded_widgets),
                        edit = { roundedWidgets.set(value = it) },
                    )
                }
            }
        }
    }
}
