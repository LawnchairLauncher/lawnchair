package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.QsbLayout
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.QsbSearchProvider
import com.android.launcher3.R

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun QsbProviderPreference() {
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val context = LocalContext.current
    val entries = remember {
        QsbSearchProvider.values().map {
            // Enabled is true if provider is anything but Google Go,
            // or if it is Google Go and the intent can be resolved.
            val enabled = it != GoogleGo || QsbLayout.resolveIntent(context, it.createSearchIntent())
            ListPreferenceEntry(it, enabled) { stringResource(id = it.name) }
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(R.string.search_provider),
    )
}
