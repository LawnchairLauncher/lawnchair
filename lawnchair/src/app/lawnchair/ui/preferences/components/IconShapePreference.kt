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

package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import com.android.launcher3.R

@ExperimentalMaterialApi
@Composable
fun IconShapePreference(
) {
    val context = LocalContext.current
    val entries = remember {
        val systemShape = IconShapeManager.getSystemIconShape(context)
        listOf<ListPreferenceEntry<IconShape>>(
            ListPreferenceEntry(systemShape) { stringResource(id = R.string.icon_shape_system) },
            ListPreferenceEntry(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
            ListPreferenceEntry(IconShape.Square) { stringResource(id = R.string.icon_shape_square) },
            ListPreferenceEntry(IconShape.RoundedSquare) { stringResource(id = R.string.icon_shape_rounded_square) },
            ListPreferenceEntry(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
            ListPreferenceEntry(IconShape.Sammy) { stringResource(id = R.string.icon_shape_sammy) },
            ListPreferenceEntry(IconShape.Teardrop) { stringResource(id = R.string.icon_shape_teardrop) },
            ListPreferenceEntry(IconShape.Cylinder) { stringResource(id = R.string.icon_shape_cylinder) },
            ListPreferenceEntry(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_cupertino) },
            )
    }

    val adapter = preferenceManager2().iconShape.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.icon_shape_label),
    )
}
