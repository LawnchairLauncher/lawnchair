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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.lawnchair.preferences.PreferenceAdapter

@Composable
fun SwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false
) {
    // TODO: Wrap overflowing text instead of using an ellipsis.
    PreferenceTemplate(
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            Switch(
                checked = adapter.state.value,
                onCheckedChange = adapter::onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary),
            )
        },
        modifier = Modifier
            .clickable(enabled) { adapter.onChange(!adapter.state.value) },
        enabled = enabled,
        showDivider = showDivider
    )
}
