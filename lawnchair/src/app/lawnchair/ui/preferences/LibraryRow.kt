package app.lawnchair.ui.preferences

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LibraryRow(label: String, description: String, url: String) {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
            .clickable {
                val webpage = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            }
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
        CompositionLocalProvider(
            LocalContentAlpha provides ContentAlpha.medium,
            LocalContentColor provides MaterialTheme.colors.onBackground
        ) {
            Text(text = description, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onBackground)
        }
    }
}