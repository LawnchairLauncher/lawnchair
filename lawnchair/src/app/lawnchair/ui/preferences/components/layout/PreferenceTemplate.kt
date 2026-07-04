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

package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/***
 * A template used to create most preference-related components in the Preference UI.
 *
 * Material Expressive
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
fun PreferenceTemplate(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    enabled: Boolean = true,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val localInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    Column(modifier) {
        SegmentedListItem(
//            selected = TODO(),
            onClick = { onClick?.invoke() },
            // Since we don't know the position of the list, we assume it's in a middle position,
            // then we clip or round the column make the list round instead.
            shapes = shapes,
            modifier = contentModifier,
            enabled = enabled,
            leadingContent = startWidget,
            trailingContent = endWidget,
            overlineContent = overlineContent,
            supportingContent = {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    description()
                }
            },
            verticalAlignment = verticalAlignment,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            colors = colors,
            elevation = ListItemDefaults.elevation(),
            contentPadding = ListItemDefaults.ContentPadding,
            interactionSource = localInteractionSource,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                LocalTextStyle provides MaterialTheme.typography.titleMedium,
            ) {
                title()
            }
        }
    }
}
