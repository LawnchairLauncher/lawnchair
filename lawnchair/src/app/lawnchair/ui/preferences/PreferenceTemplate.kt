package app.lawnchair.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceTemplate(height: Dp, showDivider: Boolean = true, content: @Composable () -> Unit) =
    Column(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }
        if (showDivider) Divider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
    }