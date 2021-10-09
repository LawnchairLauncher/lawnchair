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

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf

@Composable
fun PreferenceTemplate(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    dividerIndent: Dp = 0.dp,
    applyPaddings: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 16.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    Column {
        if (showDivider) {
            Divider(
                modifier = Modifier.padding(horizontal = 16.dp),
                startIndent = dividerIndent,
            )
        }
        Row(
            verticalAlignment = verticalAlignment,
            modifier = modifier
                .fillMaxWidth()
                .addIf(applyPaddings) {
                    padding(horizontal = horizontalPadding, vertical = verticalPadding)
                }
        ) {
            startWidget?.let {
                startWidget()
                if (applyPaddings) {
                    Spacer(modifier = Modifier.requiredWidth(16.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .addIf(!enabled) {
                        alpha(ContentAlpha.disabled)
                    }
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.onBackground,
                    LocalTextStyle provides MaterialTheme.typography.subtitle1
                ) {
                    title()
                }
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground,
                    LocalTextStyle provides MaterialTheme.typography.body2
                ) {
                    description()
                }
            }
            endWidget?.let {
                if (applyPaddings) {
                    Spacer(modifier = Modifier.requiredWidth(16.dp))
                }
                endWidget()
            }
        }
    }
}
