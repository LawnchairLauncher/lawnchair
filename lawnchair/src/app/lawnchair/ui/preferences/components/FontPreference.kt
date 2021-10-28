package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lawnchair.font.FontCache
import app.lawnchair.font.toFontFamily
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.LocalNavController

@Composable
fun FontPreference(
    adapter: PreferenceAdapter<FontCache.Font>,
    label: String,
    showDivider: Boolean = false
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        title = { Text(text = label) },
        description = {
            val font = adapter.state.value
            Text(
                text = font.fullDisplayName,
                fontFamily = font.toFontFamily()?.getOrNull()
            )
        },
        modifier = Modifier
            .clickable { navController.navigate(route = "/fontSelection/") },
        showDivider = showDivider
    )
}
