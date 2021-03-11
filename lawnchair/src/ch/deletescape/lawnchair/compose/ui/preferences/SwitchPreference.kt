package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    description: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .height(if (description != null) 64.dp else 48.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
            description?.let {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(text = it, style = MaterialTheme.typography.body2)
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}