package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import android.util.Log
import app.lawnchair.predictions.LawnchairPredictionEngine
import app.lawnchair.predictions.UsageStatsRanker
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp

object AppSearchProvider {

    const val TAG: String = "AppSearchProvider"
    const val DEBUG: Boolean = false

    const val TEXT_MATCHER_MAX_RESULTS = 32

    private val DIACRITICS_REMOVE_PATTERN = "\\p{M}+".toRegex()

    private var cachedWeights: LinkedHashMap<String, Double> = LinkedHashMap<String, Double>()
    private var cacheValid: Boolean = false

    fun search(context: Context, query: String, allApps: AllAppsList): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstCached()

        val queryNormalized = stripDiacritics(query).lowercase(Locale.getDefault())

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, hiddenApps, hiddenAppsInSearch)
        }

        maybeUpdateCache(context)

        var normalizedWeights: LinkedHashMap<String, Double>

        if (cachedWeights.isEmpty()) {
            return appResults.map { SearchResult.App(data = it.first) }
        } else {
            // Normalize the weights
            var maxWeight: Double = cachedWeights.maxOf { (_, weight) -> weight }

            val weightOffset = 1.0f

            normalizedWeights = LinkedHashMap(
                cachedWeights.mapValues { (_, weight) ->
                    (weight / maxWeight) * 4.0f
                },
            )

            return appResults.map { (appInfo, score) ->
                val packageName = appInfo.targetComponent.packageName

                val scoreExp = exp(2*(score-1.0f))

                var weight: Float

                try {
                    weight = normalizedWeights.getValue(packageName ?: "").toFloat()
                } catch (e: NoSuchElementException) {
                    weight = 0.0f
                }

                val weightFinal = weight + weightOffset
                val total = scoreExp*weightFinal

                if (DEBUG) {
                    Log.w(
                        TAG,
                        "result: packageName=%s score=%f scoreExp=%f weigth=%f weightFinal=%f total=%f"
                            .format(packageName ?: "", score, scoreExp, weight, weightFinal, total),
                    )
                }

                Pair(appInfo, total)
            }.sortedByDescending {
                it.second
            }.map {
                if (DEBUG) Log.w(TAG, "final picks: picked=%s weight=%f".format(it.first.targetComponent.packageName, it.second))
                SearchResult.App(data = it.first)
            }.take(maxAppResults)
        }
    }

    fun invalidateCache() {
        if (DEBUG) Log.d(TAG, "invalidateCache()")
        cacheValid = false
    }

    private fun maybeUpdateCache(context: Context) {
        if (!cacheValid) {
            if (DEBUG) Log.d(TAG, "maybeUpdateCache(): cacheValid=false")
            val usageStatsRanker = UsageStatsRanker(context)
            val predictionEngine = LawnchairPredictionEngine(context, usageStatsRanker)

            cachedWeights = predictionEngine.getUsageStatsWeights()
            cacheValid = true
        }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<Pair<AppInfo, Float>> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(query, stripDiacritics(it.title.toString()), matcher) }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
            .map { Pair<AppInfo, Float>(it, 1.0f) }
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<Pair<AppInfo, Float>> {
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
            .take(TEXT_MATCHER_MAX_RESULTS)
            .map {
                if (DEBUG) {
                    Log.w(
                        TAG,
                        "title=%s matchType=%s score=%f".format(
                            it.first.title.toString(),
                            it.second.type.toString(),
                            it.second.score
                        )
                    )
                }
                Pair<AppInfo, Float>(it.first, it.second.score)
            }
    }

    private fun stripDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REMOVE_PATTERN, "")
    }
}
