package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.DividerColumn

/**
 * A toggle to enable a list of preferences.
 */
@Composable
fun MainSwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    description: String? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val checked = adapter.state.value

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = androidx.compose.material.MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = adapter::onChange,
            label = label,
        )
    }
    if (description != null) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground.copy(alpha = ContentAlpha.medium),
            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
        ) {
            Text(text = description)
        }
    }
    AnimatedVisibility(
        visible = checked,
        enter = fadeIn(),
        exit = fadeOut(),
    ) { DividerColumn(color = Color.Transparent) { content() } }
}
