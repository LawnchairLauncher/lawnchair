package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.QsbLayout
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.QsbSearchProvider
import com.android.launcher3.R

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PreferenceCollectorScope.QsbProviderPreference(
    value: QsbSearchProvider,
    edit: suspend PreferenceManager2.(QsbSearchProvider) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember {
        QsbSearchProvider.values().map {
            // Enabled is true if provider is anything but Google Go,
            // or if it is Google Go and the intent can be resolved.
            val enabled = it != GoogleGo || QsbLayout.resolveIntent(context, it.createSearchIntent())
            ListPreferenceEntry2(it, enabled) { stringResource(id = it.name) }
        }
    }

    ListPreference2(
        value = value,
        entries = entries,
        label = stringResource(R.string.search_provider),
        onValueChange = { edit { this.edit(it) } },
    )
}