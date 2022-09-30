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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import com.android.launcher3.R

fun NavGraphBuilder.folderGraph(route: String) {
    preferenceGraph(route, { FolderPreferences() })
}

@Composable
fun FolderPreferences() {
    PreferenceLayout(label = stringResource(id = R.string.folders_label)) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            ColorPreference(preference = prefs2.folderColor)
            SliderPreference(
                label = stringResource(id = R.string.folder_preview_bg_opacity_label),
                adapter = prefs2.folderPreviewBackgroundOpacity.getAdapter(),
                step = 0.1F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.max_folder_columns),
                adapter = prefs2.folderColumns.getAdapter(),
                step = 1,
                valueRange = 2..5,
            )
            SliderPreference(
                label = stringResource(id = R.string.max_folder_rows),
                adapter = prefs.folderRows.getAdapter(),
                step = 1,
                valueRange = 2..5,
            )
        }
    }
}
