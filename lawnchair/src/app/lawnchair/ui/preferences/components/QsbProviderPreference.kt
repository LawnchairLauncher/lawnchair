package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.QsbSearchProvider

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PreferenceCollectorScope.QsbProviderPreference(
    value: QsbSearchProvider,
    edit: suspend PreferenceManager2.(QsbSearchProvider) -> Unit,
) {
    val entries = remember {
        QsbSearchProvider.values().map { ListPreferenceEntry2(it) { it.name } }
    }

    ListPreference2(
        value = value,
        entries = entries,
        label = "Search Provider",
        onValueChange = { edit { this.edit(it) } },
    )
}