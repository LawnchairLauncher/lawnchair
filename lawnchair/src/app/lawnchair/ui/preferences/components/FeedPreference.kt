package app.lawnchair.ui.preferences.components

import android.content.Context
import androidx.compose.runtime.Composable
import app.lawnchair.FeedBridge
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import kotlinx.collections.immutable.toImmutableList

data class ProviderInfo(
    val name: String,
    val packageName: String,
//    val icon: Drawable?
)

fun getProviders(context: Context): List<ListPreferenceEntry<String>> {
    val entries = listOf(
        ProviderInfo(
            packageName = "",
            name = "Default",
//          icon = null,
        ),
    ) +
        FeedBridge.getAvailableProviders(context).map {
            ProviderInfo(
                name = it.loadLabel(context.packageManager).toString(),
                packageName = it.packageName,
//                it.loadIcon(context.packageManager)
            )
        }

    return entries.map {
        ListPreferenceEntry(
            it.packageName,
        ) { it.name }
    }
}

@Composable
fun FeedPreference(context: Context) {
    ListPreference(
        adapter = preferenceManager().feedProvider.getAdapter(),
        entries = getProviders(context).toImmutableList(),
        label = "Feed Provider",
    )
}
