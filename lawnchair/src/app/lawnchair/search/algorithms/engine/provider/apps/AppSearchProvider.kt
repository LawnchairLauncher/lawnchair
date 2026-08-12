package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import android.util.Log
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

object AppSearchProvider {

    const val TAG: String = "AppSearchProvider"
    const val DEBUG: Boolean = false

    private val DIACRITICS_REMOVE_PATTERN = "\\p{M}+".toRegex()

    private data class CacheSnapshot(
        val weights: Map<String, Double> = emptyMap(),
        val generation: Int = 1,
    )

    @Volatile
    private var targetGeneration: Int = 0

    @Volatile
    private var snapshot: CacheSnapshot = CacheSnapshot()

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

        val localUsageScores = snapshot.weights

        if (localUsageScores.isEmpty()) {
            return appResults.take(maxAppResults).map { SearchResult.App(data = it.first) }
        } else {
            // Normalize the usage scores
            val maxUsageScore: Double = localUsageScores.maxOf { (_, weight) -> weight }
            val normalizedUsageScores = LinkedHashMap(
                localUsageScores.mapValues { (_, score) ->
                    (score / maxUsageScore)
                },
            )

            return appResults.map { (appInfo, textScore) ->
                val packageName = appInfo.targetComponent.packageName

                val usageScore = (normalizedUsageScores[packageName ?: ""] ?: 0.0).toFloat()

                val finalScore = UsageAwareRankingModel.run(textScore, usageScore)

                Pair(appInfo, finalScore)
            }.sortedByDescending {
                it.second
            }.map {
                if (DEBUG) Log.w(
                    TAG,
                    "final picks: picked=%s weight=%f".format(
                        it.first.targetComponent.packageName,
                        it.second,
                    ),
                )
                SearchResult.App(data = it.first)
            }.take(maxAppResults)
        }
    }

    fun invalidateCache() {
        if (DEBUG) Log.d(TAG, "invalidateCache()")
        targetGeneration++
    }

    private fun maybeUpdateCache(context: Context) {
        val targetGen = targetGeneration
        if (snapshot.generation != targetGen) {
            synchronized(this) {
                if (snapshot.generation != targetGen) {
                    if (DEBUG) Log.d(TAG, "maybeUpdateCache(): building for generation $targetGen")

                    val usageStatsRanker = UsageStatsRanker(context)

                    // Get weights and publish the new immutable snapshot
                    val newWeights = usageStatsRanker.getUsageStatsWeights()
                    snapshot = CacheSnapshot(newWeights, targetGen)
                }
            }

            UsageAwareRankingModel.updateCoeffs(context)
        }
    }

    private fun normalSearch(
        apps: List<AppInfo>,
        query: String,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
    ): List<Pair<AppInfo, Float>> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter {
                StringMatcherUtility.matches(
                    query,
                    stripDiacritics(it.title.toString()),
                    matcher,
                )
            }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()
            .map { Pair<AppInfo, Float>(it, 1.0f) }
    }

    private fun fuzzySearch(
        apps: List<AppInfo>,
        query: String,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
    ): List<Pair<AppInfo, Float>> {
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
            .map {
                if (DEBUG) {
                    Log.w(
                        TAG,
                        "title=%s matchType=%s score=%f".format(
                            it.first.title.toString(),
                            it.second.type.toString(),
                            it.second.score,
                        ),
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
