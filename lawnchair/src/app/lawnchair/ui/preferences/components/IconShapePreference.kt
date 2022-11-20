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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.customIconShapePreferenceGraph
import app.lawnchair.ui.preferences.preferenceGraph
import app.lawnchair.ui.preferences.subRoute
import com.android.launcher3.R

object IconShapeRoutes {
    const val CUSTOM_ICON_SHAPE_CREATOR = "customIconShapeCreator"
}

fun NavGraphBuilder.iconShapeGraph(route: String) {
    preferenceGraph(route, { IconShapePreference() }) { subRoute ->
        customIconShapePreferenceGraph(subRoute(IconShapeRoutes.CUSTOM_ICON_SHAPE_CREATOR))
    }
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
    val preferenceManager2 = preferenceManager2()
    val entries = remember { iconShapeEntries(context) }
    val iconShapeAdapter = preferenceManager2.iconShape.getAdapter()
    val customIconShape = preferenceManager2.customIconShape.asState()

    PreferenceLayout(label = stringResource(id = R.string.icon_shape_label)) {
        PreferenceGroup(
            heading = stringResource(id = R.string.custom),
        ) {
            CustomIconShapePreference(
                iconShapeAdapter = iconShapeAdapter,
            )
            ModifyCustomIconShapePreference(
                customIconShape = customIconShape.value,
            )
        }
        PreferenceGroup(
            heading = stringResource(id = R.string.presets),
        ) {
            entries.forEach { item ->
                PreferenceTemplate(
                    enabled = item.enabled,
                    title = { Text(item.label()) },
                    modifier = Modifier.clickable(item.enabled) {
                        iconShapeAdapter.onChange(newValue = item.value)
                    },
                    startWidget = {
                        RadioButton(
                            selected = item.value == iconShapeAdapter.state.value,
                            onClick = null,
                            enabled = item.enabled,
                        )
                    },
                    endWidget = {
                        IconShapePreview(iconShape = item.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomIconShapePreference(
    modifier: Modifier = Modifier,
    iconShapeAdapter: PreferenceAdapter<IconShape>,
) {
    val preferenceManager2 = preferenceManager2()

    val customIconShapeAdapter = preferenceManager2.customIconShape.getAdapter()
    val customIconShape = customIconShapeAdapter.state.value

    customIconShape?.let {
        PreferenceTemplate(
            title = { Text(stringResource(id = R.string.custom)) },
            modifier = modifier.clickable {
                iconShapeAdapter.onChange(newValue = it)
            },
            startWidget = {
                RadioButton(
                    selected = IconShape.isCustomShape(iconShapeAdapter.state.value),
                    onClick = null,
                )
            },
            endWidget = {
                IconShapePreview(iconShape = it)
            }
        )
    }
}

@Composable
private fun ModifyCustomIconShapePreference(
    modifier: Modifier = Modifier,
    customIconShape: IconShape?,
) {
    val navController = LocalNavController.current
    val route = subRoute(IconShapeRoutes.CUSTOM_ICON_SHAPE_CREATOR)

    val created = customIconShape != null

    val text = if (created) stringResource(id = R.string.custom_icon_shape_edit)
    else stringResource(id = R.string.custom_icon_shape_create)

    val icon = if (created) Icons.Rounded.Edit else Icons.Rounded.Add

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                navController.navigate(route = route)
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.secondary,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            ) {
                Text(
                    text = text,
                )
            }
            Spacer(modifier = Modifier.requiredWidth(12.dp))
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.secondary,
                contentDescription = null,
            )
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
