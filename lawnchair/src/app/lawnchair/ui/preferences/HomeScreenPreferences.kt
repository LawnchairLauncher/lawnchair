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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.homeScreenGraph(route: String) {
    preferenceGraph(route, { HomeScreenPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun HomeScreenPreferences() {
    val prefs = preferenceManager()
    pageMeta.provide(Meta(title = stringResource(id = R.string.home_screen_label)))
    PreferenceLayout {
        PreferenceGroup(heading = "General", isFirstChild = true) {
            val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
            SwitchPreference(
                prefs.minusOneEnable.getAdapter(),
                label = stringResource(id = R.string.minus_one_enable),
                description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                enabled = feedAvailable,
                showDivider = true
            )
            SwitchPreference(
                prefs.addIconToHome.getAdapter(),
                label = stringResource(id = R.string.auto_add_shortcuts_label),
                showDivider = true
            )
            SwitchPreference(
                prefs.smartSpaceEnable.getAdapter(),
                label = stringResource(id = R.string.smart_space_enable),
            )
            SwitchPreference(
                prefs.workspaceDt2s.getAdapter(),
                label = stringResource(id = R.string.workspace_dt2s),
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.home_screen_columns),
                adapter = prefs.workspaceColumns.getAdapter(),
                steps = 3,
                valueRange = 3.0F..7.0F
            )
            SliderPreference(
                label = stringResource(id = R.string.home_screen_rows),
                adapter = prefs.workspaceRows.getAdapter(),
                steps = 3,
                valueRange = 3.0F..7.0F,
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                adapter = prefs.iconSizeFactor.getAdapter(),
                steps = 9,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true
            )
            SliderPreference(
                label = stringResource(id = R.string.label_size),
                adapter = prefs.textSizeFactor.getAdapter(),
                steps = 9,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
                showDivider = false
            )
        }
    }
}
