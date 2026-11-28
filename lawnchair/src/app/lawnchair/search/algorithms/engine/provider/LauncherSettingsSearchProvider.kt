package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.content.Intent
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.data.SettingInfo
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.search.SettingsSearchCatalog
import app.lawnchair.ui.preferences.search.settingsSearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * LC: finds the launcher's own settings from the app drawer search. A result opens the
 * settings screen and highlights the setting, like the search in the settings.
 */
object LauncherSettingsSearchProvider : SearchProvider {

    override val id: String = "launcher_settings"

    private const val MIN_QUERY_LENGTH = 3
    private const val MAX_RESULTS = 4

    override fun search(context: Context, query: String): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager2.getInstance(context)
        if (query.trim().length < MIN_QUERY_LENGTH || !prefs.searchResultLauncherSettings.firstCached(prefs)) {
            emit(emptyList())
            return@flow
        }
        val results = SettingsSearchCatalog.search(context, query).mapNotNull { result ->
            val route = settingsSearchRoute(result.entry.screen) ?: return@mapNotNull null
            val intent = PreferenceActivity.createIntent(context, route)
                .putExtra(PreferenceActivity.EXTRA_SEARCH_HIGHLIGHT, result.label)
                // A fresh settings task, so a later result does not just bring back the screen
                // of an earlier one (the route and highlight are read when the activity starts).
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            SearchResult.Setting(
                SettingInfo(
                    id = "launcher_${result.entry.screen}_${result.entry.label}",
                    name = if (result.path.isEmpty()) result.label else "${result.label} · ${result.path}",
                    action = "",
                    intent = intent,
                ),
            )
        }.take(MAX_RESULTS)
        emit(results)
    }.flowOn(Dispatchers.IO)
}
