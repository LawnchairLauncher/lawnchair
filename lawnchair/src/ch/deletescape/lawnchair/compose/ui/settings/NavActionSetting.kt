package ch.deletescape.lawnchair.compose.ui.settings

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
fun NavActionSetting(
    title: String,
    subtitle: String? = null,
    navController: NavController,
    destination: String
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(if (subtitle != null) 64.dp else 48.dp)
            .fillMaxWidth()
            .clickable { navController.navigate(route = destination) }
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
        subtitle?.let {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(text = it, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onBackground)
            }
        }
    }
}