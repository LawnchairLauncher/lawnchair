package app.lawnchair.ui.preferences.components

import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.font.FontCache
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun FontPreference(
    adapter: PreferenceAdapter<FontCache.Font>,
    label: String,
    showDivider: Boolean = true
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        height = 72.dp,
        showDivider = showDivider
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable { navController.navigate(route = "/fontSelection/") }
                .padding(start = 16.dp, end = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            val context = LocalContext.current
            val textView = remember { AppCompatTextView(context) }
            val font = adapter.state.value
            LaunchedEffect(font) {
                textView.text = font.fullDisplayName
                textView.typeface = font.load()
            }
            AndroidView(factory = { textView })
        }
    }
}
