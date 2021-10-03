package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lawnchair.font.FontCache
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.AndroidText
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
            AndroidText(
                modifier = Modifier.fillMaxWidth(),
                update = {
                    val font = adapter.state.value
                    it.text = font.fullDisplayName
                    it.setFont(font)
                }
            )
        },
        modifier = Modifier
            .clickable { navController.navigate(route = "/fontSelection/") },
        showDivider = showDivider
    )
}
