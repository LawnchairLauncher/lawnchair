package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import com.patrykmichalik.opto.core.firstBlocking
import java.util.Locale

object AppSearchProvider {

    fun search(context: Context, query: String, allApps: List<AppInfo>): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstBlocking()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstBlocking()
        val maxAppResults = prefs.maxAppSearchResultCount.firstBlocking()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstBlocking()
        if (maxAppResults <= 0 || query.isBlank()) return emptyList()

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps, query, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps, query, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        return appResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val queryTextLower = query.lowercase(Locale.getDefault())
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        val visibleApps = apps.asSequence()
            .filterHiddenApps(queryTextLower, hiddenApps, hiddenAppsInSearch)
            .toList()
        val exact = visibleApps.asSequence()
            .filter { StringMatcherUtility.matches(queryTextLower, it.title.toString(), matcher) }
            .take(maxResultsCount)
            .toList()
        if (exact.size == maxResultsCount) return exact

        // Arabic hamza/diacritic variants are considered only after native matches.
        val normalizedQuery = SearchTextNormalizer.normalize(queryTextLower)
        if (normalizedQuery.isEmpty()) return exact
        val fallback = visibleApps.asSequence()
            .filter { it !in exact }
            .filter { SearchTextNormalizer.normalize(it.title.toString()).contains(normalizedQuery) }
            .take(maxResultsCount - exact.size)
            .toList()
        return exact + fallback
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val queryTextLower = query.lowercase(Locale.getDefault())
        val filteredApps = apps.asSequence()
            .filterHiddenApps(queryTextLower, hiddenApps, hiddenAppsInSearch)
            .toList()

        val normalizedQuery = SearchTextNormalizer.normalize(queryTextLower)
        if (normalizedQuery.isEmpty()) return emptyList()
        val direct = filteredApps.asSequence()
            .filter { SearchTextNormalizer.normalize(it.title.toString()).contains(normalizedQuery) }
            .sortedWith(
                compareBy(
                    { SearchTextNormalizer.normalize(it.title.toString()) != normalizedQuery },
                    { !SearchTextNormalizer.normalize(it.title.toString()).startsWith(normalizedQuery) },
                ),
            )
            .take(maxResultsCount)
            .toList()
        if (direct.size == maxResultsCount) return direct

        return direct + filteredApps.asSequence()
            .filter { it !in direct }
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(app.title.toString(), queryTextLower)
                if (matchResult.type == MatchType.NO_MATCH) null else Pair(app, matchResult)
            }
            .sortedWith(
                compareBy(
                    { it.second.type.priority },
                    { -it.second.score },
                ),
            )
            .map { it.first }
            .take(maxResultsCount - direct.size)
            .toList()
    }
}
