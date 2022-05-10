package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lawnchair.font.FontCache
import app.lawnchair.font.toFontFamily
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.ui.preferences.LocalNavController

@Composable
fun FontPreference(
    fontPref: BasePreferenceManager.FontPref,
    label: String,
    showDivider: Boolean = false
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        title = { Text(text = label) },
        description = {
            val font = fontPref.getAdapter().state.value
            Text(
                text = font.fullDisplayName,
                fontFamily = font.toFontFamily()?.getOrNull()
            )
        },
        modifier = Modifier
            .clickable { navController.navigate(route = "/fontSelection/${fontPref.key}/") },
        showDivider = showDivider
    )
}
