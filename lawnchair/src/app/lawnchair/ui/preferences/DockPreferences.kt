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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.dockGraph(route: String) {
    preferenceGraph(route, { DockPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun DockPreferences() {
    val prefs = preferenceManager()
    PreferenceLayout(label = stringResource(id = R.string.dock_label)) {
        PreferenceGroup(isFirstChild = true, heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs.enableHotseatQsb.getAdapter(),
                label = stringResource(id = R.string.hotseat_qsb_label),
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.dock_icons),
                adapter = prefs.hotseatColumns.getAdapter(),
                step = 1,
                valueRange = 3..10,
                showDivider = false
            )
        }
    }
}
