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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.util.addIf

@Composable
fun SwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true
) =
    // TODO: Wrap overflowing text instead of using an ellipsis.
    PreferenceTemplate(height = if (description != null) 72.dp else 52.dp, showDivider = showDivider) {
        Row(
            modifier = Modifier
                .clickable(enabled) { adapter.onChange(!adapter.state.value) }
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .addIf(!enabled) { alpha(ContentAlpha.disabled) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                description?.let {
                    CompositionLocalProvider(
                        LocalContentAlpha provides ContentAlpha.medium,
                        LocalContentColor provides MaterialTheme.colors.onBackground
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.body2,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Switch(
                checked = adapter.state.value,
                onCheckedChange = adapter::onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary),
            )
        }
    }