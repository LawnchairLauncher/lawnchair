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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.theme.dividerColor
import app.lawnchair.ui.util.addIf

@Composable
fun SwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    val checked = adapter.state.value
    SwitchPreference(
        checked = checked,
        onCheckedChange = adapter::onChange,
        label = label,
        description = description,
        onClick = onClick,
        enabled = enabled,
        showDivider = showDivider,
    )
}

@Composable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    PreferenceTemplate(
        modifier = Modifier
            .addIf(onClick == null) { clickable { onCheckedChange(!checked) } },
        contentModifier = Modifier
            .addIf(onClick != null) { clickable { onClick!!() } }
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            if (onClick != null) {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor())
                )
            }
            MYSwitch(
                modifier = Modifier
                    .addIf(onClick != null) { clickable { onCheckedChange(!checked) } }
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        enabled = enabled,
        showDivider = showDivider,
        applyPaddings = false
    )
}
