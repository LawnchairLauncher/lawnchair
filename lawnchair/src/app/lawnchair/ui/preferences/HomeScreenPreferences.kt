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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.homeScreenGraph(route: String) {
    preferenceGraph(route, { HomeScreenPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun HomeScreenPreferences() {
    val prefs = preferenceManager()
    PreferenceLayout(label = stringResource(id = R.string.home_screen_label)) {
        PreferenceGroup(heading = "General", isFirstChild = true) {
            val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
            SwitchPreference(
                prefs.minusOneEnable.getAdapter(),
                label = stringResource(id = R.string.minus_one_enable),
                description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                enabled = feedAvailable,
                showDivider = false
            )
            SwitchPreference(
                prefs.addIconToHome.getAdapter(),
                label = stringResource(id = R.string.auto_add_shortcuts_label),
            )
            SwitchPreference(
                prefs.smartSpaceEnable.getAdapter(),
                label = stringResource(id = R.string.smart_space_enable),
            )
            SwitchPreference(
                prefs.wallpaperScrolling.getAdapter(),
                label = stringResource(id = R.string.wallpaper_scrolling_label),
            )
            SwitchPreference(
                prefs.workspaceDt2s.getAdapter(),
                label = stringResource(id = R.string.workspace_dt2s),
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
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.home_screen_columns),
                adapter = prefs.workspaceColumns.getAdapter(),
                step = 1,
                valueRange = 3..10,
                showDivider = false
            )
            SliderPreference(
                label = stringResource(id = R.string.home_screen_rows),
                adapter = prefs.workspaceRows.getAdapter(),
                step = 1,
                valueRange = 3..10,
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                adapter = prefs.iconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
                showDivider = false
            )
            val showHomeLabels = prefs.showHomeLabels.getAdapter()
            SwitchPreference(
                showHomeLabels,
                label = stringResource(id = R.string.show_home_labels),
            )
            AnimatedVisibility(
                visible = showHomeLabels.state.value,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
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
    }
}
