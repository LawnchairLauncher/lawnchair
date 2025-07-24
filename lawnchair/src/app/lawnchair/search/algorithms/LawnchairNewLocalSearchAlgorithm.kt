package app.lawnchair.search.algorithms

import app.lawnchair.search.algorithms.engine.provider.AppSearchProvider
import app.lawnchair.search.algorithms.engine.provider.ContactsSearchProvider
import app.lawnchair.search.algorithms.engine.provider.FileSearchProvider
import app.lawnchair.search.algorithms.engine.provider.HistorySearchProvider
import app.lawnchair.search.algorithms.engine.provider.SettingsSearchProvider
import app.lawnchair.search.algorithms.engine.provider.web.WebSuggestionProvider
import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SearchLinksTarget
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.algorithms.engine.ActionsSectionBuilder
import app.lawnchair.search.algorithms.engine.AppsAndShortcutsSectionBuilder
import app.lawnchair.search.algorithms.engine.CalculationSectionBuilder
import app.lawnchair.search.algorithms.engine.ContactsSectionBuilder
import app.lawnchair.search.algorithms.engine.FilesSectionBuilder
import app.lawnchair.search.algorithms.engine.HistorySectionBuilder
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.engine.SectionBuilder
import app.lawnchair.search.algorithms.engine.SettingsSectionBuilder
import app.lawnchair.search.algorithms.engine.WebSuggestionsSectionBuilder
import app.lawnchair.search.algorithms.engine.provider.CalculatorSearchProvider
import app.lawnchair.search.algorithms.engine.provider.ShortcutSearchProvider
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull

class LawnchairNewLocalSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null

    private val appSearchProvider = AppSearchProvider
    private val shortcutSearchProvider = ShortcutSearchProvider
    private val historySearchProvider = HistorySearchProvider

    private val searchProviders: List<SearchProvider> = listOf(
        SettingsSearchProvider,
        FileSearchProvider,
        ContactsSearchProvider,
        WebSuggestionProvider,
    )

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        appState.model.enqueueModelUpdateTask { _, _, apps ->
            val appResults = appSearchProvider.search(context, query, apps)
            val shortcutResults = shortcutSearchProvider.search(context, appResults)

            currentJob?.cancel()
            currentJob = coroutineScope.launch {
                val nonAppProvidersFlow = combine(
                    searchProviders.map { it.search(context, query) }
                ) { resultsArray ->
                    resultsArray.toList().flatten()
                }

                nonAppProvidersFlow.collect { nonAppResults ->
                    val calcResult = CalculatorSearchProvider.search(context, query)
                        .firstOrNull()

                    val allResults = appResults + shortcutResults + (calcResult ?: emptyList()) + nonAppResults + generateActionResults(query)

                    val searchTargets = translateToSearchTargets(allResults)
                    val adapterItems = transformSearchResults(searchTargets)
                    withContext(Dispatchers.Main) {
                        callback.onSearchResult(query, ArrayList(adapterItems))
                    }
                }
            }
        }
    }

    override fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        currentJob?.cancel()
        currentJob = coroutineScope.launch {
            val prefs2 = PreferenceManager2.getInstance(context)
            val maxHistory = prefs2.maxRecentResultCount.firstBlocking()

            val historyResults = historySearchProvider.getRecentKeywords(context, maxHistory)

            val searchTargets = translateToSearchTargets(historyResults)
            val adapterItems = transformSearchResults(searchTargets)
            withContext(Dispatchers.Main) {
                callback.onSearchResult("", ArrayList(adapterItems))
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        currentJob?.cancel()
    }

    private fun generateActionResults(query: String): List<SearchResult.Action> {
        val actions = mutableListOf<SearchResult.Action>()
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        actions.add(SearchResult.Action.Divider())

        if (prefs.searchResultStartPageSuggestion.get()) {
            val provider = prefs2.webSuggestionProvider.firstBlocking()
            val webProvider = provider.configure(context)

            val providerName = if (webProvider is CustomWebSearchProvider) {
                webProvider.getDisplayName(context.getString(webProvider.label))
            } else {
                context.getString(webProvider.label)
            }

            actions.add(
                SearchResult.Action.WebSearch(
                    query = query,
                    providerName = providerName,
                    searchUrl = webProvider.getSearchUrl(query),
                    providerIconRes = webProvider.iconRes
                )
            )
        }

        if (SearchLinksTarget.resolveMarketSearchActivity(context) != null) {
            actions.add(SearchResult.Action.MarketSearch(query = query))
        }

        return actions
    }

    private val sectionBuilders: List<SectionBuilder> = listOf(
        AppsAndShortcutsSectionBuilder,
        WebSuggestionsSectionBuilder,
        CalculationSectionBuilder,
        ContactsSectionBuilder,
        FilesSectionBuilder,
        SettingsSectionBuilder,
        HistorySectionBuilder,
        ActionsSectionBuilder
    )

    private fun translateToSearchTargets(
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val factory = SearchTargetFactory(context)

        // The new function is just a flatMap. It's declarative and beautiful.
        return sectionBuilders.flatMap { builder ->
            builder.build(context, factory, results)
        }
    }
}
