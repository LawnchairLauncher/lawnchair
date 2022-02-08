package app.lawnchair.ui.preferences.components

import android.content.Intent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.qsb.QsbLayout
import app.lawnchair.qsb.QsbSearchProvider
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun QsbProviderPreference() {
    val context = LocalContext.current
    val entries = remember {
        QsbSearchProvider.values()
            .filter { QsbLayout.resolveSearchIntent(context, it) }
            .map { ListPreferenceEntry(it.index) { it.name } }
    }

    val prefs = preferenceManager()
    ListPreference(
        adapter = prefs.hotseatQsbProvider.getAdapter(),
        entries = entries,
        label = "Search Provider"
    )
}