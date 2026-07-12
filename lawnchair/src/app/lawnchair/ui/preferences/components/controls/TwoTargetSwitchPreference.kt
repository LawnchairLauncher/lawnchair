/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.preview.PreferenceGroupPreviewContainer
import app.lawnchair.ui.util.preview.PreviewLawnchair
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun TwoTargetSwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    switchEnabled: Boolean = enabled,
    onClick: (() -> Unit)? = null,
) {
    val checked = adapter.state.value
    TwoTargetSwitchPreference(
        checked = checked,
        onCheckedChange = adapter::onChange,
        label = label,
        modifier = modifier,
        description = description,
        onClick = onClick,
        enabled = enabled,
        switchEnabled = switchEnabled,
    )
}

/**
 * A Preference that provides a two-state toggleable option with two click targets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TwoTargetSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    switchEnabled: Boolean = enabled,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)

    val wrappedOnCheckedChange: (Boolean) -> Unit = { newValue ->
        mMSDLPlayerWrapper.playToken(if (newValue) MSDLToken.SWITCH_ON else MSDLToken.SWITCH_OFF)
        onCheckedChange(newValue)
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        enabled = enabled,
        description = description?.let { { Text(text = it) } },
        endWidget = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onClick != null) {
                    VerticalDivider(
                        modifier = Modifier.height(32.dp),
                    )
                }
                Switch(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(24.dp),
                    checked = checked,
                    onCheckedChange = wrappedOnCheckedChange,
                    enabled = switchEnabled,
                    interactionSource = interactionSource,
                    thumbContent = {
                        if (checked) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        },
        onClick = {
            if (onClick != null) {
                onClick()
            } else {
                wrappedOnCheckedChange(!checked)
            }
        },
        interactionSource = interactionSource,
    )
}

@PreviewLawnchair
@Composable
private fun TwoTargetSwitchPreferencePreview(
    @PreviewParameter(TwoTargetSwitchPreferencePreviewParameterProvider::class) checked: Boolean,
) {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            TwoTargetSwitchPreference(
                checked = checked,
                onCheckedChange = {},
                label = "Label",
                description = "Description",
                onClick = { },
            )
        }
    }
}

private class TwoTargetSwitchPreferencePreviewParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}
