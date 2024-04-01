package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink

/**
 * A toggle to enable a list of preferences.
 */
@Composable
fun MainSwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val checked = adapter.state.value

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = adapter::onChange,
            label = label,
        )
    }
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
            DividerColumn(
                color = Color.Transparent,
            ) {
                content()
            }
        }
    }
}
