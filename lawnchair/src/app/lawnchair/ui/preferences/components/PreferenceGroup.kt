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

package app.lawnchair.ui.preferences.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.util.smartBorder
import androidx.compose.material3.MaterialTheme as Material3Theme

@Composable
@ExperimentalAnimationApi
fun PreferenceGroup(
    heading: String? = null,
    isFirstChild: Boolean = false,
    description: String? = null,
    showDescription: Boolean = true,
    showDividers: Boolean = true,
    dividerStartIndent: Dp = 0.dp,
    dividerEndIndent: Dp = 0.dp,
    dividersToSkip: Int = 0,
    content: @Composable () -> Unit
) {
    PreferenceGroupHeading(heading, isFirstChild)
    val columnModifier = Modifier
        .padding(horizontal = 16.dp)
        .smartBorder(
            1.dp,
            color = Material3Theme.colorScheme.onBackground.copy(alpha = 0.16f),
            shape = MaterialTheme.shapes.large
        )
        .clip(shape = MaterialTheme.shapes.large)
    if (showDividers) {
        DividerColumn(
            modifier = columnModifier,
            startIndent = dividerStartIndent,
            endIndent = dividerEndIndent,
            content = content,
            dividersToSkip = dividersToSkip
        )
    } else {
        Column(modifier = columnModifier) {
            content()
        }
    }
    PreferenceGroupDescription(description, showDescription)
}

@Composable
fun PreferenceGroupHeading(
    heading: String? = null,
    isFirstChild: Boolean
) {
    var spacerHeight = 0
    if (heading == null) spacerHeight += 8
    if (!isFirstChild) spacerHeight += 8

    Spacer(modifier = Modifier.requiredHeight(spacerHeight.dp))
    if (heading != null) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = heading,
                style = Material3Theme.typography.titleSmall,
                color = Material3Theme.colorScheme.primary
            )
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun PreferenceGroupDescription(description: String? = null, showDescription: Boolean = true) {
    description?.let {
        AnimatedVisibility(
            visible = showDescription,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
                Text(
                    text = it,
                    style = Material3Theme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.medium)
                )
            }
        }
    }
}
