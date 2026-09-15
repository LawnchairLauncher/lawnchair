package app.mica.ui.preferences.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.mica.preferences.BasePreferenceManager
import app.mica.preferences.getAdapter
import app.mica.ui.preferences.LocalNavController
import app.mica.ui.preferences.components.layout.PreferenceTemplate
import app.mica.ui.preferences.navigation.GeneralFontSelection
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun FontPreference(
    fontPref: BasePreferenceManager.FontPref,
    label: String,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)

    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        description = {
            val font = fontPref.getAdapter().state.value
            Text(
                text = font.fullDisplayName,
                fontFamily = font.composeFontFamily,
            )
        },
        onClick = {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            navController.navigate(route = GeneralFontSelection(fontPref.key))
        },
    )
}
