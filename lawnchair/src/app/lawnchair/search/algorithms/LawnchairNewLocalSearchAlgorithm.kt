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
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchLinksTarget
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.algorithms.data.Calculation
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.engine.provider.CalculatorSearchProvider
import app.lawnchair.search.algorithms.engine.provider.ShortcutSearchProvider
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
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

                    val searchTargets = translateToSearchTargets(allResults, appResults.size)
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

            val searchTargets = translateToSearchTargets(historyResults, 0)
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

    private fun translateToSearchTargets(
        results: List<SearchResult>,
        appResultCount: Int,
    ): List<SearchTargetCompat> {
        val factory = SearchTargetFactory(context)
        val targets = mutableListOf<SearchTargetCompat>()

        val apps = results.filterIsInstance<SearchResult.App>()
        val shortcuts = results.filterIsInstance<SearchResult.Shortcut>()
        val otherResults = results.filter { it !is SearchResult.App && it !is SearchResult.Shortcut }

        if (appResultCount == 1 && shortcuts.isNotEmpty()) {
            val singleApp = apps.first()
            targets.add(factory.createAppSearchTarget(singleApp.data, asRow = true))
            targets.addAll(shortcuts.map { factory.createShortcutTarget(it.data) })
        } else {
            targets.addAll(apps.map { factory.createAppSearchTarget(it.data, asRow = false) })
        }

        if (targets.isNotEmpty() && otherResults.isNotEmpty()) {
            targets.add(factory.createHeaderTarget(SPACE))
        }

        val webSuggestions = otherResults.filterIsInstance<SearchResult.WebSuggestion>()
        if (webSuggestions.isNotEmpty()) {
            val suggestionsHeader =
                factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_suggestions))
            targets.add(suggestionsHeader)
            targets.addAll(
                webSuggestions.map {
                    factory.createWebSuggestionsTarget(
                        it.suggestion,
                        it.provider,
                    )
                },
            )
        }

        val calculator = otherResults.filterIsInstance<Calculation>()
        if (calculator.isNotEmpty()) {
            val calculatorHeader =
                factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_calculator))
            targets.add(calculatorHeader)
            targets.add(
                factory.createCalculatorTarget(calculator.first()),
            )
        }

        val contacts = otherResults.filterIsInstance<SearchResult.Contact>()
        if (contacts.isNotEmpty()) {
            val contactsHeader =
                factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_contacts_from_device))
            targets.add(contactsHeader)
            targets.addAll(contacts.map { factory.createContactsTarget(it.data) })
        }

        val settings = otherResults.filterIsInstance<SearchResult.Setting>()
        if (settings.isNotEmpty()) {
            val settingsHeader =
                factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_settings_entry_from_device))
            targets.add(settingsHeader)
            targets.addAll(settings.mapNotNull { factory.createSettingsTarget(it.data) })
        }

        val files = otherResults.filterIsInstance<SearchResult.File>()
        if (files.isNotEmpty()) {
            val filesHeader =
                factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_files))
            targets.add(filesHeader)
            targets.addAll(files.map { factory.createFilesTarget(it.data) })
        }

        val history = otherResults.filterIsInstance<SearchResult.History>()
        if (history.isNotEmpty()) {
            val historyHeader = factory.createHeaderTarget(context.getString(R.string.search_pref_result_history_title))
            targets.add(historyHeader)
        }

        val marketSearch = otherResults.filterIsInstance<SearchResult.Action.MarketSearch>()
        if (marketSearch.isNotEmpty()) {
            factory.createMarketSearchTarget((marketSearch.first()).query)?.let { targets.add(it) }
        }

        val webSearch = otherResults.filterIsInstance<SearchResult.Action.WebSearch>()
        if (webSearch.isNotEmpty()) {
            val action = webSearch.first()

            factory.createWebSearchActionTarget(
                query = action.query,
                providerName = action.providerName,
                searchUrl = action.searchUrl,
                providerIconRes = action.providerIconRes
            )
        }

        val header = otherResults.filterIsInstance<SearchResult.Action.Header>()
        if (header.isNotEmpty()) {
            header.forEach {
                factory.createHeaderTarget(
                    it.pkg, it.title
                )
            }
        }

        return targets
    }
}
