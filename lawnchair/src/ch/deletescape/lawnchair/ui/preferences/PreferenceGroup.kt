package ch.deletescape.lawnchair.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
fun PreferenceGroup(heading: String? = null, showDivider: Boolean = false, content: @Composable () -> Unit) {
    Column {
        if (showDivider) Divider()
        heading?.let {
            Column(
                modifier = Modifier
                    .height(44.dp)
                    .padding(start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = it.toUpperCase(Locale.ROOT),
                        style = MaterialTheme.typography.overline
                    )
                }
                Spacer(modifier = Modifier.requiredHeight(12.dp))
            }
        }
        content()
    }
}