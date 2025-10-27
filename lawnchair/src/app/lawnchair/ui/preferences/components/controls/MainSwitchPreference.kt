package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

/**
 * A toggle to enable a list of preferences.
 */
@Composable
fun MainSwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MainSwitchPreference(
        checked = adapter.state.value,
        onCheckedChange = adapter::onChange,
        label = label,
        modifier = modifier,
        description = description,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun MainSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MainSwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
    )

    ExpandAndShrink(description != null) {
        if (description != null) {
            Row(
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Crossfade(targetState = checked, label = "") { targetState ->
        if (targetState) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun MainSwitchPreference(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (checked && enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        PreferenceTemplate(
            modifier = Modifier
                .clickable(
                    enabled = enabled,
                    indication = ripple(),
                    interactionSource = interactionSource,
                ) {
                    onCheckedChange(!checked)
                },
            contentModifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp)
                .padding(start = 16.dp),
            title = { Text(text = label, style = MaterialTheme.typography.titleMedium) },
            endWidget = {
                Switch(
                    modifier = Modifier
                        .padding(all = 16.dp)
                        .height(24.dp),
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
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
            },
            enabled = enabled,
            applyPaddings = false,
        )
    }
}
