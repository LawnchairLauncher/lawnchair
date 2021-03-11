package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceGroup(heading: String? = null, showDivider: Boolean = false, content: @Composable () -> Unit) {
    Column {
        if (showDivider) Divider()
        heading?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .padding(start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(text = it, style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
            }
        }
        content()
    }
}