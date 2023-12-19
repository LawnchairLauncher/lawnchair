package app.lawnchair.ui.preferences.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.FeedBridge
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.collections.immutable.toImmutableList

data class ProviderInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
)

fun getProviders(context: Context): List<ProviderInfo> {
    val providers = listOf(
        ProviderInfo(
            packageName = "",
            name = context.getString(R.string.feed_default),
            icon = CustomAdaptiveIconDrawable.wrapNonNull(AppCompatResources.getDrawable(context, R.drawable.ic_launcher_home)!!),
        ),
    ) +
        FeedBridge.getAvailableProviders(context).map {
            ProviderInfo(
                name = it.loadLabel(context.packageManager).toString(),
                packageName = it.packageName,
                icon = CustomAdaptiveIconDrawable.wrapNonNull(it.loadIcon(context.packageManager)),
            )
        }

    return providers
}

fun getEntries(context: Context): List<ListPreferenceEntry<String>> {
    val providers = getProviders(context)
    return providers.map {
        ListPreferenceEntry(
            value = it.packageName,
            endWidget = {
                if (it.icon != null) {
                    Image(
                        painter = rememberDrawablePainter(drawable = it.icon),
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
    val entries = remember { getEntries(context).toImmutableList() }
    val selected = entries.firstOrNull {
        it.value == adapter.state.value
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(R.string.feed_provider),
        endWidget = selected?.endWidget
    )
}
