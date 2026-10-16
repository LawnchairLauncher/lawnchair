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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FoldMirrorNote
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.isFoldHomeMirrored
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@Composable
fun FolderPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.folders_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()
        val folderIconShapeAdapter = prefs2.folderShape.getAdapter()
        val folderIconShapeSubtitle = iconShapeEntries(context)
            .firstOrNull { it.value == folderIconShapeAdapter.state.value }
            ?.label?.invoke()
            ?: stringResource(id = R.string.custom)
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.folder_shape_label),
                destination = GeneralIconShape(ShapeRoute.FOLDER_SHAPE),
                subtitle = folderIconShapeSubtitle,
                endWidget = {
                    IconShapePreview(iconShape = folderIconShapeAdapter.state.value)
                },
            )
            ColorPreference(preference = prefs2.folderColor)
            SliderPreference(
                label = stringResource(id = R.string.folder_preview_bg_opacity_label),
                adapter = prefs2.folderPreviewBackgroundOpacity.getAdapter(),
                step = 0.1F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            SliderPreference(
                label = stringResource(id = R.string.folder_bg_opacity_label),
                adapter = prefs2.folderBackgroundOpacity.getAdapter(),
                step = 0.1F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            val columnsLabel = stringResource(id = R.string.max_folder_columns)
            val rowsLabel = stringResource(id = R.string.max_folder_rows)
            // "Same as cover" uses the folded folder grid on the large screen too
            val homeMirrored = isFoldable && isFoldHomeMirrored()
            val folderColumnsAdapter = prefs2.folderColumns.getAdapter()
            SliderPreference(
                label = if (isFoldable) stringResource(id = R.string.state_folded, columnsLabel) else columnsLabel,
                adapter = folderColumnsAdapter,
                step = 1,
                valueRange = 2..5,
            )
            // The unfolded columns and rows follow the folded ones until set, but their sliders don't
            // see a folded change by themselves: read them again when the folded value changes.
            if (isFoldable) {
                key(folderColumnsAdapter.state.value) {
                    SliderPreference(
                        label = stringResource(id = R.string.state_unfolded, columnsLabel),
                        adapter = prefs2.folderColumnsUnfolded.getAdapter(),
                        step = 1,
                        valueRange = 2..6,
                        enabled = !homeMirrored,
                    )
                }
            }
            val folderRowsAdapter = prefs.folderRows.getAdapter()
            SliderPreference(
                label = if (isFoldable) stringResource(id = R.string.state_folded, rowsLabel) else rowsLabel,
                adapter = folderRowsAdapter,
                step = 1,
                valueRange = 2..5,
            )
            if (isFoldable) {
                key(folderRowsAdapter.state.value) {
                    SliderPreference(
                        label = stringResource(id = R.string.state_unfolded, rowsLabel),
                        adapter = prefs2.folderRowsUnfolded.getAdapter(),
                        step = 1,
                        valueRange = 2..5,
                        enabled = !homeMirrored,
                    )
                }
            }
            if (homeMirrored) {
                FoldMirrorNote()
            }
        }

        val homeScreenLabelsAdapter = prefs2.showIconLabelsOnHomeScreenFolder.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SwitchPreference(
                adapter = homeScreenLabelsAdapter,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(visible = homeScreenLabelsAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelFolderSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
    }
}
