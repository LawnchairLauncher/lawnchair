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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.R
import com.android.launcher3.Utilities

object HomeScreenRoutes {
    const val GRID = "grid"
}

@ExperimentalAnimationApi
fun NavGraphBuilder.homeScreenGraph(route: String) {
    preferenceGraph(route, { HomeScreenPreferences() }) { subRoute ->
        homeScreenGridGraph(route = subRoute(HomeScreenRoutes.GRID))
    }
}

@ExperimentalAnimationApi
@Composable
fun HomeScreenPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
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
                adapter = prefs2.dt2s.getAdapter(),
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
            val showStatusBarAdapter = prefs2.showStatusBar.getAdapter()
            SwitchPreference(
                adapter = showStatusBarAdapter,
                label = stringResource(id = R.string.show_status_bar),
            )
            AnimatedVisibility(
                visible = showStatusBarAdapter.state.value,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SwitchPreference(
                    adapter = prefs2.darkStatusBar.getAdapter(),
                    label = stringResource(id = R.string.dark_status_bar_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.what_to_show)) {
            val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
            SwitchPreference(
                adapter = prefs2.enableFeed.getAdapter(),
                label = stringResource(id = R.string.minus_one_enable),
                description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                enabled = feedAvailable,
            )
            SwitchPreference(
                adapter = prefs2.enableSmartspace.getAdapter(),
                label = stringResource(id = R.string.smart_space_enable),
            )
            SwitchPreference(
                adapter = prefs2.showTopShadow.getAdapter(),
                label = stringResource(id = R.string.show_sys_ui_scrim),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                adapter = prefs2.homeIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            val homeScreenLabelsAdapter = prefs2.showIconLabelsOnHomeScreen.getAdapter()
            SwitchPreference(
                adapter = homeScreenLabelsAdapter,
                label = stringResource(id = R.string.show_home_labels),
            )
            AnimatedVisibility(
                visible = homeScreenLabelsAdapter.state.value,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
        if (!Utilities.ATLEAST_S) {
            PreferenceGroup(heading = stringResource(id = R.string.widget_button_text)) {
                SwitchPreference(
                    adapter = prefs2.roundedWidgets.getAdapter(),
                    label = stringResource(id = R.string.force_rounded_widgets),
                )
            }
        }
    }
}
