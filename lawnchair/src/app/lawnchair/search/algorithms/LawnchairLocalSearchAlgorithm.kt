package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.CALCULATOR
import app.lawnchair.search.adapter.CONTACT
import app.lawnchair.search.adapter.ERROR
import app.lawnchair.search.adapter.FILES
import app.lawnchair.search.adapter.HEADER_JUSTIFY
import app.lawnchair.search.adapter.HISTORY
import app.lawnchair.search.adapter.LOADING
import app.lawnchair.search.adapter.SETTINGS
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.adapter.WEB_SUGGESTION
import app.lawnchair.search.algorithms.data.Calculation
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.data.RecentKeyword
import app.lawnchair.search.algorithms.data.SettingInfo
import app.lawnchair.search.algorithms.data.WebSearchProvider
import app.lawnchair.search.algorithms.data.calculateEquationFromString
import app.lawnchair.search.algorithms.data.findContactsByName
import app.lawnchair.search.algorithms.data.findSettingsByNameAndAction
import app.lawnchair.search.algorithms.data.getRecentKeyword
import app.lawnchair.search.algorithms.data.queryFilesInMediaStore
import app.lawnchair.search.model.SearchResult
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.isDefaultLauncher
import app.lawnchair.util.requestContactPermissionGranted
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LawnchairLocalSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private val searchTargetFactory = SearchTargetFactory(context)

    private var hiddenApps: Set<String> = emptySet()

    private var hiddenAppsInSearch = ""

    private var searchApps = true
    private var enableFuzzySearch = false
    private var useWebSuggestions = true
    private var webSuggestionsProvider = ""

    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val pref2 = PreferenceManager2.getInstance(context)

    private var maxAppResultsCount = 5
    private var maxPeopleCount = 10
    private var maxWebSuggestionsCount = 3
    private var maxFilesCount = 3
    private var maxSettingsEntryCount = 5
    private var maxRecentResultCount = 2
    private var maxWebSuggestionDelay = 200

    val coroutineScope = CoroutineScope(context = Dispatchers.IO + SupervisorJob())

    init {
        pref2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        pref2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        pref2.hiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            hiddenAppsInSearch = it
        }

        useWebSuggestions = prefs.searchResultStartPageSuggestion.get()
        searchApps = prefs.searchResultApps.get()

        pref2.webSuggestionProvider.onEach(launchIn = coroutineScope) {
            webSuggestionsProvider = it.toString()
        }

        pref2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxAppResultsCount = it
        }
        pref2.maxFileResultCount.onEach(launchIn = coroutineScope) {
            maxFilesCount = it
        }
        pref2.maxPeopleResultCount.onEach(launchIn = coroutineScope) {
            maxPeopleCount = it
        }
        pref2.maxWebSuggestionResultCount.onEach(launchIn = coroutineScope) {
            maxWebSuggestionsCount = it
        }
        pref2.maxSettingsEntryResultCount.onEach(launchIn = coroutineScope) {
            maxSettingsEntryCount = it
        }
        pref2.maxRecentResultCount.onEach(launchIn = coroutineScope) {
            maxRecentResultCount = it
        }
        pref2.maxWebSuggestionDelay.onEach(launchIn = coroutineScope) {
            maxWebSuggestionDelay = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        appState.model.enqueueModelUpdateTask(
            object : LauncherModel.ModelUpdateTask {
                override fun execute(
                    app: ModelTaskController,
                    dataModel: BgDataModel,
                    apps: AllAppsList,
                ) {
                    coroutineScope.launch(Dispatchers.Main) {
                        getAllSearchResults(apps.data, query.trimEnd(), prefs).let { allResults ->
                            callback.onSearchResult(query, ArrayList(allResults))
                        }
                    }
                }
            },
        )
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    private suspend fun getAllSearchResults(
        apps: MutableList<AppInfo>,
        query: String,
        prefs: PreferenceManager,
    ): List<BaseAllAppsAdapter.AdapterItem> {
        val allResults = mutableListOf<SearchTargetCompat>()

        if (searchApps) {
            getAppSearchResults(apps, query).let { appResults ->
                allResults.addAll(appResults)
            }
        }
        getLocalSearchResults(query, prefs).let { localResults ->
            allResults.addAll(localResults)
        }
        getSearchLinks(query).let { otherResults ->
            allResults.addAll(otherResults)
        }

        setFirstItemQuickLaunch(allResults)
        val finalResults = transformSearchResults(allResults).toList()

        return finalResults
    }

    private fun getAppSearchResults(
        apps: MutableList<AppInfo>,
        query: String,
    ): List<SearchTargetCompat> {
        val searchTargets = mutableListOf<SearchTargetCompat>()
        val appResults = performAppSearch(apps, query)

        parseAppSearchResults(appResults, searchTargets)

        return searchTargets
    }

    private suspend fun getLocalSearchResults(
        query: String,
        prefs: PreferenceManager,
    ): List<SearchTargetCompat> {
        val searchTargets = mutableListOf<SearchTargetCompat>()
        val localSearchResults = performDeviceLocalSearch(query, prefs)
        parseLocalSearchResults(localSearchResults, searchTargets)
        return searchTargets
    }

    private fun getSearchLinks(
        query: String,
    ): List<SearchTargetCompat> {
        val searchTargets = mutableListOf<SearchTargetCompat>()

        searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        if (useWebSuggestions) {
            searchTargets.add(
                searchTargetFactory.createWebSearchTarget(
                    query,
                    webSuggestionsProvider,
                ),
            )
        }
        searchTargetFactory.createMarketSearchTarget(query)?.let { searchTargets.add(it) }

        return searchTargets
    }

    private fun parseAppSearchResults(
        appResults: List<AppInfo>,
        searchTargets: MutableList<SearchTargetCompat>,
    ) {
        if (appResults.isNotEmpty()) {
            appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)

            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { SearchUtils.getShortcuts(it, context) }
                if (shortcuts != null) {
                    if (shortcuts.isNotEmpty()) {
                        searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
                        singleAppResult.let {
                            searchTargets.add(
                                searchTargetFactory.createAppSearchTarget(
                                    it,
                                    true,
                                ),
                            )
                        }
                        searchTargets.addAll(shortcuts.map(searchTargetFactory::createShortcutTarget))
                    }
                }
            }
            searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        }
    }

    private fun parseLocalSearchResults(
        localSearchResults: MutableList<SearchResult>,
        searchTargets: MutableList<SearchTargetCompat>,
    ) {
        val suggestionProvider = webSuggestionsProvider
        val suggestions = filterByType(localSearchResults, WEB_SUGGESTION)
        if (suggestions.isNotEmpty()) {
            val suggestionsHeader =
                searchTargetFactory.createHeaderTarget(context.getString(R.string.all_apps_search_result_suggestions))
            searchTargets.add(suggestionsHeader)
            searchTargets.addAll(
                suggestions.map {
                    searchTargetFactory.createWebSuggestionsTarget(
                        it.resultData as String,
                        suggestionProvider,
                    )
                },
            )
        }

        val calculator = filterByType(localSearchResults, CALCULATOR).firstOrNull()
        val calcData = calculator?.resultData as? Calculation
        if (calcData != null && calcData.isValid) {
            val calculatorHeader =
                searchTargetFactory.createHeaderTarget(context.getString(R.string.all_apps_search_result_calculator))
            searchTargets.add(calculatorHeader)
            searchTargets.add(
                searchTargetFactory.createCalculatorTarget(calcData),
            )
        }

        val contacts = filterByType(localSearchResults, CONTACT)
        if (contacts.isNotEmpty()) {
            val contactsHeader =
                searchTargetFactory.createHeaderTarget(context.getString(R.string.all_apps_search_result_contacts_from_device))
            searchTargets.add(contactsHeader)
            searchTargets.addAll(contacts.map { searchTargetFactory.createContactsTarget(it.resultData as ContactInfo) })
        }

        val settings = filterByType(localSearchResults, SETTINGS)
        if (settings.isNotEmpty()) {
            val settingsHeader =
                searchTargetFactory.createHeaderTarget(context.getString(R.string.all_apps_search_result_settings_entry_from_device))
            searchTargets.add(settingsHeader)
            searchTargets.addAll(settings.mapNotNull { searchTargetFactory.createSettingsTarget(it.resultData as SettingInfo) })
        }

        // todo refactor to only show when search is first clicked
        val recentKeyword = filterByType(localSearchResults, HISTORY)
        if (recentKeyword.isNotEmpty()) {
            val recentKeywordHeader = searchTargetFactory.createHeaderTarget(
                context.getString(R.string.search_pref_result_history_title),
                HEADER_JUSTIFY,
            )
            searchTargets.add(recentKeywordHeader)
            searchTargets.addAll(
                recentKeyword.map {
                    searchTargetFactory.createSearchHistoryTarget(
                        it.resultData as RecentKeyword,
                        suggestionProvider,
                    )
                },
            )
        }

        val files = filterByType(localSearchResults, FILES)
        if (files.isNotEmpty()) {
            val filesHeader =
                searchTargetFactory.createHeaderTarget(context.getString(R.string.all_apps_search_result_files))
            searchTargets.add(filesHeader)
            searchTargets.addAll(files.map { searchTargetFactory.createFilesTarget(it.resultData as IFileInfo) })
        }
    }

    private fun performAppSearch(
        apps: MutableList<AppInfo>,
        query: String,
    ) = if (enableFuzzySearch) {
        SearchUtils.fuzzySearch(apps, query, maxAppResultsCount, hiddenApps, hiddenAppsInSearch)
    } else {
        SearchUtils.normalSearch(apps, query, maxAppResultsCount, hiddenApps, hiddenAppsInSearch)
    }

    private suspend fun performDeviceLocalSearch(
        query: String,
        prefs: PreferenceManager,
    ): MutableList<SearchResult> =
        withContext(Dispatchers.IO) {
            val results = ArrayList<SearchResult>()

            if (prefs.searchResultCalculator.get()) {
                val calculations = calculateEquationFromString(query)
                results.add(SearchResult(CALCULATOR, calculations))
            }

            val contactDeferred = async {
                if (prefs.searchResultPeople.get() && requestContactPermissionGranted(
                        context,
                        prefs,
                    )
                ) {
                    findContactsByName(context, query, maxPeopleCount)
                        .map { SearchResult(CONTACT, it) }
                } else {
                    emptyList()
                }
            }

            val filesDeferred = async {
                if (prefs.searchResultFiles.get() && checkAndRequestFilesPermission(
                        context,
                        prefs,
                    )
                ) {
                    queryFilesInMediaStore(context, keyword = query, maxResult = maxFilesCount)
                        .toList()
                        .map { SearchResult(FILES, it) }
                } else {
                    emptyList()
                }
            }

            val settingsDeferred = async {
                if (prefs.searchResultSettingsEntry.get()) {
                    findSettingsByNameAndAction(query, maxSettingsEntryCount)
                        .map { SearchResult(SETTINGS, it) }
                } else {
                    emptyList()
                }
            }

            val startPageSuggestionsDeferred = async {
                try {
                    val timeout = maxWebSuggestionDelay.toLong()
                    val result = withTimeoutOrNull(timeout) {
                        if (prefs.searchResultStartPageSuggestion.get()) {
                            WebSearchProvider.fromString(webSuggestionsProvider)
                                .getSuggestions(query, maxWebSuggestionsCount).map {
                                    SearchResult(
                                        WEB_SUGGESTION,
                                        it,
                                    )
                                }
                        } else {
                            emptyList()
                        }
                    }
                    result ?: emptyList()
                } catch (e: TimeoutCancellationException) {
                    emptyList()
                }
            }

            if (prefs.searchResulRecentSuggestion.get()) {
                getRecentKeyword(
                    context,
                    query,
                    maxRecentResultCount,
                    object : app.lawnchair.search.algorithms.data.SearchCallback {
                        override fun onSearchLoaded(items: List<Any>) {
                            results.addAll(items.map { SearchResult(HISTORY, it) })
                        }

                        override fun onSearchFailed(error: String) {
                            results.add(SearchResult(ERROR, error))
                        }

                        override fun onLoading() {
                            results.add(SearchResult(LOADING, "Loading"))
                        }
                    },
                )
            }

            results.addAll(contactDeferred.await())
            results.addAll(filesDeferred.await())
            results.addAll(settingsDeferred.await())
            results.addAll(startPageSuggestionsDeferred.await())

            results
        }

    private fun filterByType(results: List<SearchResult>, type: String): List<SearchResult> {
        return results.filter { it.resultType == type }
    }
}
