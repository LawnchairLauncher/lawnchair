package app.lawnchair.ui.preferences.components

import androidx.compose.runtime.Composable
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import kotlinx.collections.immutable.toPersistentList

object HiddenAppsInSearch {
    const val NEVER = "off"
    const val IF_NAME_TYPED = "if_name_typed"
    const val ALWAYS = "on"
}

val hiddenAppsInSearchEntries = sequenceOf(
    ListPreferenceEntry(HiddenAppsInSearch.NEVER) { "Never" },
    ListPreferenceEntry(HiddenAppsInSearch.IF_NAME_TYPED) { "If Full Name is Typed" },
    ListPreferenceEntry(HiddenAppsInSearch.ALWAYS) { "Always" },
)
    .toPersistentList()

@Composable
fun HiddenAppsInSearchPreference() {
    ListPreference(
        adapter = preferenceManager2().hiddenAppsInSearch.getAdapter(),
        entries = hiddenAppsInSearchEntries,
        label = "Show Hidden Apps in Search Results"
    )
}
