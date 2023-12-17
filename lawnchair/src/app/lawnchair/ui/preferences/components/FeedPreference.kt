package app.lawnchair.ui.preferences.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.FeedBridge
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.collections.immutable.toImmutableList

data class ProviderInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

fun getEntries(context: Context): List<ProviderInfo> {
    val entries = listOf(
        ProviderInfo(
            packageName = "",
            name = context.getString(R.string.feed_default),
            icon = null,
        ),
    ) +
        FeedBridge.getAvailableProviders(context).map {
            ProviderInfo(
                name = it.loadLabel(context.packageManager).toString(),
                packageName = it.packageName,
                it.loadIcon(context.packageManager)
            )
        }

    return entries
}

fun getProvidersList(context: Context): List<ListPreferenceEntry<String>> {
    val entries = getEntries(context)
    return entries.map {
        ListPreferenceEntry(
            value = it.packageName,
            icon = {
                if (it.icon != null) {
                    Image(
                        painter = DrawablePainter(it.icon),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(48.dp),
                    )
                }
            },
            label = { it.name },
        )
    }
}

@Composable
fun FeedPreference(context: Context) {
    val adapter = preferenceManager().feedProvider.getAdapter()
    val providers = getProvidersList(context).toImmutableList()

    ListPreference(
        adapter = adapter,
        entries = providers,
        label = stringResource(R.string.feed_provider),
    )
}
