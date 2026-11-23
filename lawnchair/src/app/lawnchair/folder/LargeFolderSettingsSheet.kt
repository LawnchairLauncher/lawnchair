/*
 * Copyright 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo

/**
 * Settings of a single folder: its size (rows x columns, 1x1 being a regular folder) and, for
 * large folders, what happens with apps that don't fit in the tile.
 */
@Composable
fun LargeFolderSettingsSheet(
    launcher: Launcher,
    icon: FolderIcon,
    modifier: Modifier = Modifier,
) {
    val info = icon.mInfo
    val maxSpan = remember(icon) { LargeFolderController.getMaxSpan(icon) }
    var columns by remember { mutableIntStateOf(info.spanX) }
    var rows by remember { mutableIntStateOf(info.spanY) }
    var scroll by remember { mutableStateOf(info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL)) }
    var horizontal by remember {
        mutableStateOf(info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL))
    }
    var showScrollBar by remember {
        mutableStateOf(!info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_HIDE_SCROLL_BAR))
    }

    fun applySize(newColumns: Int, newRows: Int) {
        LargeFolderController.resize(launcher, icon, newColumns, newRows)
        // The resize may have failed (no room), so always show the actual size.
        columns = info.spanX
        rows = info.spanY
        scroll = info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL)
        horizontal = info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL)
        showScrollBar = !info.hasOption(FolderInfo.FLAG_LARGE_FOLDER_HIDE_SCROLL_BAR)
    }

    Column(
        modifier = modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth(),
    ) {
        Text(
            text = info.title?.toString()?.takeIf { it.isNotBlank() }
                ?: stringResource(id = R.string.large_folder_settings),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
        )

        PreferenceGroup(heading = stringResource(id = R.string.large_folder_size_label)) {
            StepperPreference(
                label = stringResource(id = R.string.large_folder_columns),
                value = columns,
                range = 1..maxSpan.x,
                onValueChange = { applySize(it, rows) },
            )
            StepperPreference(
                label = stringResource(id = R.string.large_folder_rows),
                value = rows,
                range = 1..maxSpan.y,
                onValueChange = { applySize(columns, it) },
            )
        }

        if (columns > 1 || rows > 1) {
            PreferenceGroup(heading = stringResource(id = R.string.large_folder_behaviour_label)) {
                RadioPreference(
                    label = stringResource(id = R.string.large_folder_behaviour_expand),
                    selected = !scroll,
                    onClick = {
                        LargeFolderController.setOption(
                            launcher,
                            icon,
                            FolderInfo.FLAG_LARGE_FOLDER_SCROLL,
                            false,
                        )
                        scroll = false
                    },
                )
                RadioPreference(
                    label = stringResource(id = R.string.large_folder_behaviour_scroll),
                    selected = scroll,
                    onClick = {
                        LargeFolderController.setOption(
                            launcher,
                            icon,
                            FolderInfo.FLAG_LARGE_FOLDER_SCROLL,
                            true,
                        )
                        scroll = true
                    },
                )
            }

            if (scroll) {
                PreferenceGroup(
                    heading = stringResource(id = R.string.large_folder_scroll_direction_label),
                ) {
                    RadioPreference(
                        label = stringResource(id = R.string.large_folder_scroll_vertical),
                        selected = !horizontal,
                        onClick = {
                            LargeFolderController.setOption(
                                launcher,
                                icon,
                                FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL,
                                false,
                            )
                            horizontal = false
                        },
                    )
                    RadioPreference(
                        label = stringResource(id = R.string.large_folder_scroll_horizontal),
                        selected = horizontal,
                        onClick = {
                            LargeFolderController.setOption(
                                launcher,
                                icon,
                                FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL,
                                true,
                            )
                            horizontal = true
                        },
                    )
                }
                PreferenceGroup {
                    SwitchPreference(
                        checked = showScrollBar,
                        onCheckedChange = { checked ->
                            LargeFolderController.setOption(
                                launcher,
                                icon,
                                FolderInfo.FLAG_LARGE_FOLDER_HIDE_SCROLL_BAR,
                                !checked,
                            )
                            showScrollBar = checked
                        },
                        label = stringResource(id = R.string.large_folder_show_scroll_bar),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StepperPreference(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    PreferenceTemplate(
        title = { Text(text = label) },
        endWidget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange(value - 1) },
                    enabled = value > range.first,
                ) {
                    Icon(imageVector = Icons.Rounded.Remove, contentDescription = null)
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                IconButton(
                    onClick = { onValueChange(value + 1) },
                    enabled = value < range.last,
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                }
            }
        },
    )
}

@Composable
private fun RadioPreference(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        title = { Text(text = label) },
        startWidget = {
            RadioButton(selected = selected, onClick = null)
        },
        onClick = onClick,
    )
}
