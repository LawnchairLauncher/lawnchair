package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import android.icu.text.Transliterator
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.data.AppInfo
import com.patrykmichalik.opto.core.firstBlocking
import java.util.LinkedHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded index of prepared labels and aliases. Rebuilt entries follow label/locale/alias changes. */
object AppSearchProvider {
    private val transliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII") }
    private fun romanize(text: String): String = synchronized(transliterator) { runCatching { transliterator.transliterate(text) }.getOrDefault(text) }
    private val index = object : LinkedHashMap<String, List<PreparedSearchText>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PreparedSearchText>>?) = size > 1024
    }

    suspend fun search(context: Context, query: String, allApps: List<AppInfo>): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        return matchApps(
            allApps,
            query,
            prefs.maxAppSearchResultCount.firstBlocking(),
            prefs.hiddenApps.firstBlocking(),
            prefs.hiddenAppsInSearch.firstBlocking(),
            prefs.enableFuzzySearch.firstBlocking(),
            ExperienceStore.get(context).read().aliases,
        ).map { SearchResult.App(it) }
    }

    suspend fun matchApps(
        apps: List<AppInfo>,
        query: String,
        maxResults: Int,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
        fuzzy: Boolean,
        aliases: Map<String, String>,
    ): List<AppInfo> {
        if (maxResults <= 0 || query.isBlank()) return emptyList()
        val preparedQuery = PreparedSearchText.from(query)
        val latinQuery = PreparedSearchText.from(romanize(query))
        val coroutineContext = currentCoroutineContext()
        val matches = apps.asSequence().filterHiddenApps(query.lowercase(java.util.Locale.ROOT), hiddenApps, hiddenAppsInSearch)
            .mapNotNull { app ->
                coroutineContext.ensureActive()
                val label = app.title.toString()
                val alias = aliases[app.toComponentKey().toString()].orEmpty()
                val key = "${app.toComponentKey()}|$label|$alias"
                val texts = synchronized(index) { index[key] } ?: run {
                    val originals = listOf(label) + alias.split(',').filter { it.isNotBlank() }
                    val prepared = (originals + originals.map { romanize(it) })
                        .distinct().map(PreparedSearchText::from)
                    synchronized(index) { index[key] = prepared }
                    prepared
                }
                val best = texts.flatMap { text ->
                    listOf(AppMatcher.match(text, preparedQuery, fuzzy), AppMatcher.match(text, latinQuery, fuzzy))
                }.filter { it.type != MatchType.NO_MATCH }
                    .minWithOrNull(compareBy<MatchResult> { it.type.priority }.thenByDescending { it.score })
                best?.let { Triple(app, it.type.priority, it.score) }
            }.toList()
        return matches.sortedWith(compareBy<Triple<AppInfo, Int, Float>> { it.second }.thenByDescending { it.third })
            .take(maxResults.coerceAtMost(100)).map { it.first }
    }
}
