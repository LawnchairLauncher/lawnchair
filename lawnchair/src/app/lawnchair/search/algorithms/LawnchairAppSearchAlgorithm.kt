package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.algorithms.engine.CalendarSectionBuilder
import app.lawnchair.search.algorithms.engine.provider.CalendarSearchProvider
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)

    // todo maybe use D.I.?
    private val searchTargetFactory = SearchTargetFactory(context)

    private var hiddenApps: Set<String> = setOf()

    private var hiddenAppsInSearch = ""
    private var enableFuzzySearch = false
    private var maxResultsCount = 5

    private val prefs2 = PreferenceManager2.getInstance(context)

    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    private var modelRetryJob: Job? = null
    private var currentJob: Job? = null

    init {
        prefs2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        prefs2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        prefs2.hiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            hiddenAppsInSearch = it
        }
        prefs2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxResultsCount = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        modelRetryJob?.cancel()
        modelRetryJob = appState.model.runWhenModelLoaded(coroutineScope) { apps ->
            // Track the delivery job so cancel(true) (e.g. from a drawer close) can stop a
            // late-arriving result from being delivered after reset. See issue #7325.
            currentJob?.cancel()
            currentJob = coroutineScope.launch(Dispatchers.Main) {
                val appTargets = getAppTargets(apps.data, query)
                callback.onSearchResult(query, buildAdapterItems(query, appTargets, emptyList()))
                val calendarTargets = withContext(Dispatchers.Default) {
                    val events = CalendarSearchProvider.search(context, query).first()
                    CalendarSectionBuilder.build(context, searchTargetFactory, events)
                }
                if (calendarTargets.isNotEmpty()) {
                    callback.onSearchResult(query, buildAdapterItems(query, appTargets, calendarTargets))
                }
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            modelRetryJob?.cancel()
            currentJob?.cancel()
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun getAppTargets(
        apps: MutableList<AppInfo>,
        query: String,
    ): List<SearchTargetCompat> {
        val appResults = if (enableFuzzySearch) {
            SearchUtils.fuzzySearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        } else {
            SearchUtils.normalSearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        }

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { SearchUtils.getShortcuts(it, context) }
                if (shortcuts != null && shortcuts.isNotEmpty()) {
                    // Show app as a row alongside its shortcuts (no duplicate icon)
                    singleAppResult.let { searchTargets.add(searchTargetFactory.createAppSearchTarget(it, true)) }
                    searchTargets.addAll(shortcuts.map(searchTargetFactory::createShortcutTarget))
                } else {
                    // No shortcuts: show as icon
                    appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
                }
            } else {
                // Multiple results: show as icons
                appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
            }
            searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        }

        return searchTargets
    }

    private fun buildAdapterItems(
        query: String,
        appTargets: List<SearchTargetCompat>,
        calendarTargets: List<SearchTargetCompat>,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        // The market target is created fresh on every delivery.
        // setFirstItemQuickLaunch marks targets in place, so reusing it would keep quick launch on
        // the market link even after calendar results arrive above it.
        val marketTarget = searchTargetFactory.createMarketSearchTarget(query)
        val searchTargets = appTargets + calendarTargets + listOfNotNull(marketTarget)
        setFirstItemQuickLaunch(searchTargets)
        return ArrayList(transformSearchResults(searchTargets))
    }
}
