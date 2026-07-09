package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.preview.PreferenceGroupPreviewContainer
import app.lawnchair.ui.util.preview.PreviewLawnchair

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainSwitchPreference(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val contentPadding = 16.dp // This must match [PreferenceGroup]'s padding
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier.padding(horizontal = contentPadding),
    ) {
        SegmentedListItem(
            onClick = { onCheckedChange(!checked) },
            selected = checked,
            shapes = ListItemDefaults.shapes().copy(
                shape = MaterialTheme.shapes.medium,
                selectedShape = MaterialTheme.shapes.extraLarge,
                pressedShape = CircleShape,
                focusedShape = CircleShape,
                hoveredShape = CircleShape,
            ),
            enabled = enabled,
            trailingContent = {
                Switch(
                    modifier = Modifier
                        .padding(top = contentPadding, bottom = contentPadding, start = contentPadding)
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
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@PreviewLawnchair
@Composable
private fun MainSwitchPreferenceCheckedPreview() {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            MainSwitchPreference(
                checked = true,
                onCheckedChange = {},
                label = "Main Switch Preference",
                description = "Description of the main switch preference",
            ) {
                Text(
                    text = "Expanded content",
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@PreviewLawnchair
@Composable
private fun MainSwitchPreferenceUncheckedPreview() {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            MainSwitchPreference(
                checked = false,
                onCheckedChange = {},
                label = "Main Switch Preference",
                description = "Description of the main switch preference",
            ) {
                Text(
                    text = "Expanded content",
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }
    }
}
