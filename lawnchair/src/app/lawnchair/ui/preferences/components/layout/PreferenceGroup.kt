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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    description: String? = null,
    showDescription: Boolean = true,
    showDividers: Boolean = true,
    dividerStartIndent: Dp = 0.dp,
    dividerEndIndent: Dp = 0.dp,
    dividersToSkip: Int = 0,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        PreferenceGroupHeading(heading)
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            if (showDividers) {
                DividerColumn(
                    startIndent = dividerStartIndent,
                    endIndent = dividerEndIndent,
                    content = content,
                    dividersToSkip = dividersToSkip,
                )
            } else {
                Column {
                    content()
                }
            }
        }
        PreferenceGroupDescription(description = description, showDescription = showDescription)
    }
}

@Composable
fun PreferenceGroupHeading(
    heading: String?,
    modifier: Modifier = Modifier,
) {
    if (heading != null) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Spacer(modifier = modifier.requiredHeight(8.dp))
    }
}

@Composable
fun PreferenceGroupDescription(
    modifier: Modifier = Modifier,
    description: String? = null,
    showDescription: Boolean = true,
) {
    description?.let {
        ExpandAndShrink(
            modifier = modifier,
            visible = showDescription,
            content = {
                Row(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
