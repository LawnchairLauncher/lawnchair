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
    val showStatusBar: Boolean
    val showTopShadow: Boolean
    val dt2s: Boolean
    val homeIconSizeFactor: Float
    val showIconLabelsOnHomeScreen: Boolean
    val homeIconLabelSizeFactor: Float
    val enableSmartspace: Boolean
    val enableFeed: Boolean
}

@Composable
fun HomeScreenPreferenceCollector(content: @Composable HomeScreenPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val darkStatusBar by preferenceManager.darkStatusBar.state()
    val roundedWidgets by preferenceManager.roundedWidgets.state()
    val showStatusBar by preferenceManager.showStatusBar.state()
    val showTopShadow by preferenceManager.showTopShadow.state()
    val dt2s by preferenceManager.dt2s.state()
    val homeIconSizeFactor by preferenceManager.homeIconSizeFactor.state()
    val showIconLabelsOnHomeScreen by preferenceManager.showIconLabelsOnHomeScreen.state()
    val homeIconLabelSizeFactor by preferenceManager.homeIconLabelSizeFactor.state()
    val enableSmartspace by preferenceManager.enableSmartspace.state()
    val enableFeed by preferenceManager.enableFeed.state()
    ifNotNull(
        darkStatusBar, roundedWidgets,
        showStatusBar, showTopShadow,
        dt2s, homeIconSizeFactor,
        showIconLabelsOnHomeScreen, homeIconLabelSizeFactor,
        enableSmartspace, enableFeed,
    ) {
        object : HomeScreenPreferenceCollectorScope {
            override val darkStatusBar = it[0] as Boolean
            override val roundedWidgets = it[1] as Boolean
            override val showStatusBar = it[2] as Boolean
            override val showTopShadow = it[3] as Boolean
            override val dt2s = it[4] as Boolean
            override val homeIconSizeFactor = it[5] as Float
            override val showIconLabelsOnHomeScreen = it[6] as Boolean
            override val homeIconLabelSizeFactor = it[7] as Float
            override val enableSmartspace = it[8] as Boolean
            override val enableFeed = it[9] as Boolean
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
                SwitchPreference2(
                    checked = dt2s,
                    edit = { dt2s.set(value = it) },
                    label = stringResource(id = R.string.workspace_dt2s),
                )
                val columns by prefs.workspaceColumns.getAdapter()
                val rows by prefs.workspaceRows.getAdapter()
                NavigationActionPreference(
                    label = stringResource(id = R.string.home_screen_grid),
                    destination = subRoute(name = HomeScreenRoutes.GRID),
                    subtitle = stringResource(id = R.string.x_by_y, columns, rows),
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.status_bar_label)) {
                SwitchPreference2(
                    checked = showStatusBar,
                    label = stringResource(id = R.string.show_status_bar),
                    edit = { showStatusBar.set(value = it) },
                )
                AnimatedVisibility(
                    visible = showStatusBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SwitchPreference2(
                        checked = darkStatusBar,
                        label = stringResource(id = R.string.dark_status_bar_label),
                        edit = { darkStatusBar.set(value = it) },
                    )
                }
            }
            PreferenceGroup(heading = stringResource(id = R.string.what_to_show)) {
                val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
                SwitchPreference2(
                    checked = enableFeed,
                    edit = { enableFeed.set(value = it) },
                    label = stringResource(id = R.string.minus_one_enable),
                    description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                    enabled = feedAvailable,
                )
                SwitchPreference2(
                    checked = enableSmartspace,
                    edit = { enableSmartspace.set(value = it) },
                    label = stringResource(id = R.string.smart_space_enable),
                )
                SwitchPreference2(
                    checked = showTopShadow,
                    label = stringResource(id = R.string.show_sys_ui_scrim),
                    edit = { showTopShadow.set(value = it) },
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SliderPreference2(
                    label = stringResource(id = R.string.icon_size),
                    value = homeIconSizeFactor,
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                    edit = { homeIconSizeFactor.set(value = it) },
                )
                SwitchPreference2(
                    checked = showIconLabelsOnHomeScreen,
                    edit = { showIconLabelsOnHomeScreen.set(value = it) },
                    label = stringResource(id = R.string.show_home_labels),
                )
                AnimatedVisibility(
                    visible = showIconLabelsOnHomeScreen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SliderPreference2(
                        label = stringResource(id = R.string.label_size),
                        value = homeIconLabelSizeFactor,
                        edit = { homeIconLabelSizeFactor.set(value = it) },
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
