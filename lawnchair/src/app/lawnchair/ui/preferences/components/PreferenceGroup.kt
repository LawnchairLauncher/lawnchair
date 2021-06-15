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
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lawnchair.util.smartBorder

@Composable
@ExperimentalAnimationApi
fun PreferenceGroup(
    heading: String? = null,
    isFirstChild: Boolean = false,
    description: String? = null,
    showDescription: Boolean = true,
    content: @Composable () -> Unit
) {
    PreferenceGroupHeading(heading, isFirstChild)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .smartBorder(
                1.dp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.12F),
                shape = MaterialTheme.shapes.large
            )
            .clip(shape = MaterialTheme.shapes.large)
    ) {
        content()
    }
    PreferenceGroupDescription(description, showDescription)
}

@Composable
fun PreferenceGroupHeading(heading: String? = null, isFirstChild: Boolean) {
    Spacer(
        modifier = Modifier.requiredHeight(
            if (isFirstChild) {
                if (heading != null) 0.dp else 8.dp
            } else {
                if (heading != null) 8.dp else 16.dp
            }
        )
    )
    heading?.let {
        Column(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalContentColor provides MaterialTheme.colors.onBackground
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
            }
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
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
    }
}
