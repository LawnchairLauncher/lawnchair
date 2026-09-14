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

package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralCustomIconShapeCreator
import com.android.launcher3.R

@Keep // This is refed by a Kotlin serializer, we must keep it's fully qualified name.
enum class ShapeRoute {
    APP_SHAPE,
    FOLDER_SHAPE,
}

/**
 * @return The list of all [IconShape]s each wrapped inside a [ListPreferenceEntry].
 */
fun iconShapeEntries(context: Context): List<ListPreferenceEntry<IconShape>> {
    return listOf(
        ListPreferenceEntry(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
        ListPreferenceEntry(IconShape.RoundedSquare) { stringResource(id = R.string.icon_shape_rounded_square) },
        ListPreferenceEntry(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
        // The continuous-curvature corner iOS uses. Folders follow it without
        // anything further: LawnchairThemeManager reads one shape for both.
        ListPreferenceEntry(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_cupertino) },
    )
}

@Composable
fun ShapePreference(
    modifier: Modifier = Modifier,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    val context = LocalContext.current
    val entries = remember { iconShapeEntries(context) }
    val shapeAdapter = preferenceManager2().iconShape.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.icon_shape_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            entries.forEach { item ->
                PreferenceTemplate(
                    title = { Text(item.label()) },
                    startWidget = {
                        RadioButton(
                            selected = item.value == shapeAdapter.state.value,
                            onClick = null,
                        )
                    },
                    endWidget = {
                        IconShapePreview(iconShape = item.value)
                    },
                    onClick = { shapeAdapter.onChange(newValue = item.value) },
                )
            }
        }
    }
}

@Composable
private fun CustomIconShapePreferenceOption(
    iconShapeAdapter: PreferenceAdapter<IconShape>,
    customIconShape: IconShape,
    modifier: Modifier = Modifier,
) {
    PreferenceTemplate(
        title = { Text(stringResource(id = R.string.custom)) },
        modifier = modifier,
        startWidget = {
            RadioButton(
                selected = IconShape.isCustomShape(iconShapeAdapter.state.value),
                onClick = null,
            )
        },
        endWidget = {
            IconShapePreview(iconShape = customIconShape)
        },
        onClick = {
            iconShapeAdapter.onChange(newValue = customIconShape)
        },
    )
}

@Composable
private fun ModifyCustomIconShapePreference(
    customIconShape: IconShape?,
    currentTab: ShapeRoute,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val route = GeneralCustomIconShapeCreator(selectedId = currentTab)

    val created = customIconShape != null

    val text = stringResource(
        when (currentTab) {
            ShapeRoute.APP_SHAPE -> if (created) R.string.custom_icon_shape_edit else R.string.custom_icon_shape_create
            ShapeRoute.FOLDER_SHAPE -> if (created) R.string.custom_folder_shape_edit else R.string.custom_folder_shape_create
        },
    )

    val icon = if (created) Icons.Rounded.Edit else Icons.Rounded.Add

    PreferenceTemplate(
        onClick = { navController.navigate(route = route) },
        modifier = modifier,
        title = {
            Text(text = text)
        },
        startWidget = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
    )
}

/**
 * Draws a preview of an [IconShape].
 */
@Composable
fun IconShapePreview(
    iconShape: IconShape,
    modifier: Modifier = Modifier,
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
