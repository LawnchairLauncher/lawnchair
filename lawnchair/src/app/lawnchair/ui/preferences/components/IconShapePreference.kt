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

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.preferenceGraph
import com.android.launcher3.R

fun NavGraphBuilder.iconShapeGraph(route: String) {
    preferenceGraph(route, { IconShapePreference() })
}

/**
 * @return The list of all [IconShape]s each wrapped inside a [ListPreferenceEntry].
 */
fun iconShapeEntries(context: Context): List<ListPreferenceEntry<IconShape>> {
    val systemShape = IconShapeManager.getSystemIconShape(context)
    return listOf(
        // Organized as seen in /lawnchair/res/values/strings.xml
        ListPreferenceEntry(systemShape) { stringResource(id = R.string.icon_shape_system) },
        ListPreferenceEntry(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
        ListPreferenceEntry(IconShape.Cylinder) { stringResource(id = R.string.icon_shape_cylinder) },
        ListPreferenceEntry(IconShape.Diamond) { stringResource(id = R.string.icon_shape_diamond) },
        ListPreferenceEntry(IconShape.Egg) { stringResource(id = R.string.icon_shape_egg) },
        ListPreferenceEntry(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_cupertino) },
        ListPreferenceEntry(IconShape.Octagon) { stringResource(id = R.string.icon_shape_octagon) },
        ListPreferenceEntry(IconShape.Sammy) { stringResource(id = R.string.icon_shape_sammy) },
        ListPreferenceEntry(IconShape.RoundedSquare) { stringResource(id = R.string.icon_shape_rounded_square) },
        ListPreferenceEntry(IconShape.SharpSquare) { stringResource(id = R.string.icon_shape_sharp_square) },
        ListPreferenceEntry(IconShape.Square) { stringResource(id = R.string.icon_shape_square) },
        ListPreferenceEntry(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
        ListPreferenceEntry(IconShape.Teardrop) { stringResource(id = R.string.icon_shape_teardrop) },
    )
}

@Composable
fun IconShapePreference(
) {
    val context = LocalContext.current
    val entries = remember { iconShapeEntries(context) }
    val adapter = preferenceManager2().iconShape.getAdapter()

    PreferenceLayout(label = stringResource(id = R.string.icon_shape_label)) {
        PreferenceGroup {
            entries.forEach { item ->
                PreferenceTemplate(
                    enabled = item.enabled,
                    title = { Text(item.label()) },
                    modifier = Modifier.clickable(item.enabled) {
                        adapter.onChange(newValue = item.value)
                    },
                    startWidget = {
                        RadioButton(
                            selected = item.value == adapter.state.value,
                            onClick = null,
                            enabled = item.enabled,
                        )
                    },
                    endWidget = {
                        IconShapePreview(iconShape = item.value)
                    }
                )
            }
        }
    }

}

/**
 * Draws a preview of an [IconShape].
 */
@Composable
fun IconShapePreview(
    modifier: Modifier = Modifier,
    iconShape: IconShape,
    strokeColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
) {

    val path = iconShape.getMaskPath().asComposePath()

    var translated = remember { false }
    fun translatePath(canvasWidth: Float, canvasHeight: Float) {
        if (!translated) {
            translated = true
            val pathHeight = path.getBounds().size.height
            val pathWidth = path.getBounds().size.width
            path.translate(
                Offset(
                    x = (canvasWidth - pathWidth) / 2,
                    y = (canvasHeight - pathHeight) / 2,
                ),
            )
        }
    }

    Canvas(
        modifier = modifier.requiredSize(48.dp),
    ) {
        translatePath(
            canvasWidth = size.width,
            canvasHeight = size.height,
        )
        drawPath(
            path = path,
            color = fillColor,
        )
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 4f),
        )
    }
}
