/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.folder

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

enum class FolderPreviewGrid(
    val itemCount: Int,
    val sideLength: Int,
    @StringRes val labelResourceId: Int,
) {
    TWO_BY_TWO(
        itemCount = 4,
        sideLength = 2,
        labelResourceId = R.string.folder_preview_grid_2x2,
    ),
    THREE_BY_THREE(
        itemCount = 9,
        sideLength = 3,
        labelResourceId = R.string.folder_preview_grid_3x3,
    ),
    ;

    companion object {
        fun valuesList() = entries.toList()

        fun fromString(value: String?): FolderPreviewGrid =
            valuesList().firstOrNull { it.name == value } ?: TWO_BY_TWO

        fun preferenceEntries(): List<ListPreferenceEntry<FolderPreviewGrid>> = valuesList().map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResourceId) }
        }
    }
}
