package app.lawnchair.omni.experience

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class AppPickerState(
    val apps: List<AppInfo> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val retry: () -> Unit = {},
)

/** Refresh live editors after package/profile/locale or hidden-app changes. No package calls on Main. */
@Composable
internal fun rememberAppPicker(): AppPickerState {
    val context = LocalContext.current
    val inventory = remember(context) { OmniAppInventory.get(context) }
    val revision by inventory.revision.collectAsStateWithLifecycle()
    val hidden by remember(context) { PreferenceManager2.getInstance(context).hiddenApps.get() }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val locale = LocalConfiguration.current.locales[0]
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState(AppPickerState(), revision, hidden, locale, retry) {
        value = AppPickerState()
        value = try {
            AppPickerState(apps = inventory.visible(), loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppPickerState(loading = false, failed = true)
        }
    }
    return state.copy(retry = { retry++ })
}

/** ICU normalization runs outside composition, and a replaced query cannot publish stale matches. */
@Composable
internal fun rememberMatchingApps(apps: List<AppInfo>, query: String, aliases: Map<String, String> = emptyMap()): List<AppInfo> {
    val matches by produceState(emptyList<AppInfo>(), apps, query, aliases) {
        value = withContext(Dispatchers.Default) {
            val needle = SearchTextNormalizer.normalize(query.trim())
            if (needle.isEmpty()) return@withContext apps
            apps.filter { app ->
                ensureActive()
                val text = "${app.title} ${app.componentName?.packageName.orEmpty()} ${aliases[app.toComponentKey().toString()].orEmpty()}"
                SearchTextNormalizer.normalize(text).contains(needle)
            }
        }
    }
    return matches
}

@Composable
internal fun AppPickerNotice(state: AppPickerState, noMatches: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.omni_apps_loading))
            }

            state.failed -> {
                Text(stringResource(R.string.omni_apps_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = state.retry) { Text(stringResource(R.string.omni_retry)) }
            }

            noMatches -> Text(stringResource(R.string.omni_apps_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
