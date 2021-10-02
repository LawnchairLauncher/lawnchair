package app.lawnchair.ui.preferences.components

import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.clickable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.font.FontCache
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.LocalNavController

@Composable
fun FontPreference(
    adapter: PreferenceAdapter<FontCache.Font>,
    label: String,
    showDivider: Boolean = true
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        title = { Text(text = label) },
        description = {
            val context = LocalContext.current
            val textView = remember { AppCompatTextView(context) }
            val font = adapter.state.value
            LaunchedEffect(font) {
                textView.text = font.fullDisplayName
                textView.typeface = font.load()
            }
            AndroidView(factory = { textView })
        },
        modifier = Modifier
            .clickable { navController.navigate(route = "/fontSelection/") },
        showDivider = showDivider
    )
}
