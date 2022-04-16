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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.state
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SliderPreference2
import app.lawnchair.util.ifNotNull
import app.lawnchair.util.rememberGridOption
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.state

@ExperimentalAnimationApi
fun NavGraphBuilder.folderGraph(route: String) {
    preferenceGraph(route, { FolderPreferences() })
}

interface FolderPreferenceCollectorScope : PreferenceCollectorScope {
    val folderPreviewBackgroundOpacity: Float
    val folderColumns: Int
}

@Composable
fun FolderPreferenceCollector(content: @Composable FolderPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val gridOption = rememberGridOption()
    val folderPreviewBackgroundOpacity by preferenceManager.folderPreviewBackgroundOpacity.state()
    val folderColumns by preferenceManager.folderColumns.state(gridOption = gridOption)
    ifNotNull(folderPreviewBackgroundOpacity, folderColumns) {
        object : FolderPreferenceCollectorScope {
            override val folderPreviewBackgroundOpacity = it[0] as Float
            override val folderColumns = it[1] as Int
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@ExperimentalAnimationApi
@Composable
fun FolderPreferences() {
    FolderPreferenceCollector {
        PreferenceLayout(label = stringResource(id = R.string.folders_label)) {
            val prefs = preferenceManager()
            PreferenceGroup(
                heading = stringResource(id = R.string.general_label),
                isFirstChild = true
            ) {
                SliderPreference2(
                    label = stringResource(id = R.string.folder_preview_bg_opacity_label),
                    value = folderPreviewBackgroundOpacity,
                    edit = { folderPreviewBackgroundOpacity.set(value = it) },
                    step = 0.1F,
                    valueRange = 0F..1F,
                    showAsPercentage = true,
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.grid)) {
                SliderPreference2(
                    label = stringResource(id = R.string.max_folder_columns),
                    value = folderColumns,
                    edit = { folderColumns.set(value = it) },
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
}
