package app.lawnchair.ui.preferences.search

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceSearchScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.delay

/** LC: one searchable setting. Strings are resource names, resolved at runtime. */
internal data class SettingsSearchEntry(
    val label: String,
    val description: String?,
    val screen: String,
    val screenTitle: String,
    val isHeading: Boolean,
)

internal data class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val label: String,
    val description: String?,
    val path: String,
)

/**
 * LC: the setting to highlight after jumping to it from the search. Matched against the
 * label of the preferences on the opened screen.
 */
object SettingsSearchHighlight {
    private const val MAX_AGE_MS = 5000L

    var target by mutableStateOf<String?>(null)
        private set
    private var setAt = 0L

    fun set(label: String) {
        target = label
        setAt = SystemClock.uptimeMillis()
    }

    /** True while [label] waits to be highlighted; a jump that found nothing expires. */
    fun isTarget(label: String?): Boolean = label != null &&
        target == label &&
        SystemClock.uptimeMillis() - setAt < MAX_AGE_MS

    fun clear(label: String?) {
        if (target == label) target = null
    }
}

/**
 * LC: highlights the preference with [label] when the search jumped to it: scrolls it into
 * view and flashes its background twice.
 */
@Composable
fun rememberSearchHighlight(label: String?): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val active = SettingsSearchHighlight.isTarget(label)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // Give the screen time to lay out before scrolling.
        delay(350)
        requester.bringIntoView()
        repeat(2) {
            flash.animateTo(0.25f, tween(durationMillis = 250))
            flash.animateTo(0f, tween(durationMillis = 450))
        }
        SettingsSearchHighlight.clear(label)
    }
    val color = MaterialTheme.colorScheme.primary
    return Modifier
        .bringIntoViewRequester(requester)
        .drawWithContent {
            drawContent()
            // On top: preferences draw their own opaque background.
            if (flash.value > 0f) drawRect(color.copy(alpha = flash.value))
        }
}

/** LC: the searchable settings with their texts in the current language. */
internal object SettingsSearchCatalog {

    private var cache: Pair<String, List<SettingsSearchResult>>? = null

    @Synchronized
    fun all(context: Context): List<SettingsSearchResult> {
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        cache?.let { (cachedLocale, results) -> if (cachedLocale == locale) return results }
        return load(context).also { cache = locale to it }
    }

    /** Settings matching [query]: matches in the name first, then in the description or screen. */
    fun search(context: Context, query: String): List<SettingsSearchResult> {
        val search = query.trim()
        if (search.isEmpty()) return emptyList()
        val all = all(context)
        val byLabel = all.filter { it.label.contains(search, ignoreCase = true) }
        val other = all.filter {
            it !in byLabel &&
                (it.description?.contains(search, ignoreCase = true) == true || it.path.contains(search, ignoreCase = true))
        }
        return byLabel + other
    }

    @SuppressLint("DiscouragedApi")
    private fun Context.stringByName(name: String?): String? {
        if (name == null) return null
        val id = resources.getIdentifier(name, "string", packageName)
        return if (id == 0) null else getString(id)
    }

    private fun load(context: Context): List<SettingsSearchResult> = settingsSearchEntries.mapNotNull { entry ->
        val label = context.stringByName(entry.label) ?: return@mapNotNull null
        val title = context.stringByName(entry.screenTitle)
        SettingsSearchResult(
            entry = entry,
            label = label,
            description = context.stringByName(entry.description),
            path = if (title == null || title == label) "" else title,
        )
    }.distinctBy { it.label to it.path }
}

/** LC: searches all settings by name and description; a result opens its screen. */
@Composable
fun SettingsSearchScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val msdlPlayer = MSDLPlayerWrapper.INSTANCE.get(context)
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val results = remember(search) { SettingsSearchCatalog.search(context, search) }

    PreferenceSearchScaffold(
        value = query,
        onValueChange = { query = it },
        modifier = modifier,
        placeholder = {
            Text(
                text = stringResource(id = R.string.settings_search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { padding ->
        PreferenceLazyColumn(padding) {
            if (search.isNotEmpty() && results.isEmpty()) {
                preferenceGroupItems(count = 1, isFirstChild = true) {
                    PreferenceTemplate(title = { Text(text = stringResource(id = R.string.settings_search_no_results)) })
                }
            }
            preferenceGroupItems(items = results, isFirstChild = true) { _, result ->
                PreferenceTemplate(
                    title = { Text(text = result.label) },
                    description = {
                        val text = listOfNotNull(result.path.ifEmpty { null }, result.description).joinToString(" · ")
                        if (text.isNotEmpty()) Text(text = text, maxLines = 2)
                    },
                    onClick = {
                        val route = settingsSearchRoute(result.entry.screen) ?: return@PreferenceTemplate
                        msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                        SettingsSearchHighlight.set(result.label)
                        navController.navigate(route)
                    },
                )
            }
        }
    }
}
