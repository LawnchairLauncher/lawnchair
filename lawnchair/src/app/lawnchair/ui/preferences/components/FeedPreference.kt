package app.lawnchair.ui.preferences.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

data class ProviderInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
)

fun getProviders(context: Context) = FeedBridge.getAvailableProviders(context).map {
    ProviderInfo(
        name = it.loadLabel(context.packageManager).toString(),
        packageName = it.packageName,
        icon = CustomAdaptiveIconDrawable.wrapNonNull(it.loadIcon(context.packageManager)),
    )
}

fun getEntries(context: Context) = getProviders(context).map {
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
}.toList()

@Composable
fun FeedPreference(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adapter = preferenceManager().feedProvider.getAdapter()
    val preferredPackage = adapter.state.value
    val entries = remember { getEntries(context) }
    val resolvedPackage = remember(preferredPackage) {
        FeedBridge.getInstance(context).resolveBridge(preferredPackage)?.packageName ?: "com.google.android.googlequicksearchbox"
    }
    Log.d("FEEDDDDD", "FeedPreference: $preferredPackage, $resolvedPackage")
    val resolvedEntry = entries.firstOrNull {
        it.value == resolvedPackage
    }

    ListPreference(
        value = resolvedEntry?.value ?: "",
        onValueChange = adapter::onChange,
        entries = entries,
        label = stringResource(R.string.feed_provider),
        modifier = modifier,
        endWidget = resolvedEntry?.endWidget,
    )
}
