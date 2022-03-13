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
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R

@ExperimentalMaterialApi
@Composable
fun PreferenceCollectorScope.IconShapePreference(
    value: IconShape,
    edit: suspend PreferenceManager2.(IconShape) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember {
        val systemShape = IconShapeManager.getSystemIconShape(context)
        listOf<ListPreferenceEntry2<IconShape>>(
            ListPreferenceEntry2(systemShape) { stringResource(id = R.string.icon_shape_system) },
            ListPreferenceEntry2(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
            ListPreferenceEntry2(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_rounded_square) },
            ListPreferenceEntry2(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
        )
    }

    ListPreference2(
        value = value,
        entries = entries,
        label = stringResource(id = R.string.icon_shape_label),
        onValueChange = { edit { this.edit(it) } },
    )
}
