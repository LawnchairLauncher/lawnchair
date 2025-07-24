package app.lawnchair.search.algorithms.engine.provider.web

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

object WebSuggestionProvider : SearchProvider {
    override val id = "web_suggestions"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank() || !prefs.searchResultStartPageSuggestion.get()) {
            return emptyFlow()
        }

        val provider = prefs2.webSuggestionProvider.firstBlocking()

        val webProvider = provider
            .configure(context)

        // 4. Now we can safely use it.
        return webProvider.getSuggestions(query)
            .map { suggestions ->
                suggestions.map { suggestion ->
                    SearchResult.WebSuggestion(suggestion = suggestion, provider = webProvider.id)
                }
            }
    }
}
