package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import java.text.Normalizer
import java.util.Locale

object AppSearchProvider {

    fun search(context: Context, query: String, allApps: AllAppsList): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstCached()

        val queryNormalized = stripDiacritics(query).lowercase(Locale.getDefault())

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        return appResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(query, stripDiacritics(it.title.toString()), matcher) }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val filteredApps = apps.asSequence()
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()

        return filteredApps
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(stripDiacritics(app.title.toString()), query)
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

    private fun stripDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace("\\p{M}", "")
    }
}
