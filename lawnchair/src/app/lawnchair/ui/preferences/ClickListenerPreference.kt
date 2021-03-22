package app.lawnchair.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.navigate

@Composable
fun ClickListenerPreference(
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true
) =
    PreferenceTemplate(height = if (subtitle != null) 72.dp else 52.dp, showDivider = showDivider) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 16.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
            subtitle?.let {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = it, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onBackground)
                }
            }
        }
    }