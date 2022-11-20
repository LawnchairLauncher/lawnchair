package app.lawnchair.ui.preferences.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WarningPreference(
    modifier: Modifier = Modifier,
    text: String,
) {
    PreferenceTemplate(
        modifier = modifier,
        title = {},
        description = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
        },
        startWidget = {
            Icon(
                imageVector = Icons.Rounded.Warning,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )
        },
    )
}
