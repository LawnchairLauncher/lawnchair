package app.lawnchair.ui.preferences.components

import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.font.FontCache
import app.lawnchair.font.toTypeface
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.LocalNavController
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder

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
            val font = adapter.state.value
            val typeface = font.toTypeface()
            if (typeface != null) {
                AndroidView(
                    factory = { context ->
                        AppCompatTextView(context).apply {
                            text = font.fullDisplayName
                            this.typeface = typeface.getOrNull()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = font.fullDisplayName,
                    modifier = Modifier
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade()
                        )
                )
            }
        },
        modifier = Modifier
            .clickable { navController.navigate(route = "/fontSelection/") },
        showDivider = showDivider
    )
}
