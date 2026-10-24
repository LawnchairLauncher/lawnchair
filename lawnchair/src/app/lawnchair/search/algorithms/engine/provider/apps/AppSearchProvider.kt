package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.SearchUtils
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import app.lawnchair.search.algorithms.normalizeForSearch
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo

object AppSearchProvider {

    fun search(context: Context, query: String, allApps: AllAppsList): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstCached()

        val queryNormalized = normalizeForSearch(query)

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        return appResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> = SearchUtils.matchApps(apps.asSequence().filterHiddenApps(query, hiddenApps, hiddenAppsInSearch), query, maxResultsCount)

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val filteredApps = apps.asSequence()
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()

        return filteredApps
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(normalizeForSearch(app.title.toString()), query)
                if (matchResult.type == MatchType.NO_MATCH) null else Pair(app, matchResult)
            }
            .sortedWith(
                compareBy(
                    { it.second.type.priority },
                    { -it.second.score },
                ),
            )
            .map { it.first }
            .take(maxResultsCount)
    }
}
