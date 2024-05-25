package app.lawnchair.search.algorithms

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Handler
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.search.adapter.CALCULATOR
import app.lawnchair.search.adapter.CONTACT
import app.lawnchair.search.adapter.ERROR
import app.lawnchair.search.adapter.FILES
import app.lawnchair.search.adapter.GenerateSearchTarget
import app.lawnchair.search.adapter.HEADER_JUSTIFY
import app.lawnchair.search.adapter.HISTORY
import app.lawnchair.search.adapter.LOADING
import app.lawnchair.search.adapter.SETTINGS
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchResult
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.WEB_SUGGESTION
import app.lawnchair.search.adapter.createSearchTarget
import app.lawnchair.search.algorithms.data.Calculation
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.data.RecentKeyword
import app.lawnchair.search.algorithms.data.SettingInfo
import app.lawnchair.search.algorithms.data.calculateEquationFromString
import app.lawnchair.search.algorithms.data.findContactsByName
import app.lawnchair.search.algorithms.data.findSettingsByNameAndAction
import app.lawnchair.search.algorithms.data.getRecentKeyword
import app.lawnchair.search.algorithms.data.getStartPageSuggestions
import app.lawnchair.search.algorithms.data.queryFilesInMediaStore
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.isDefaultLauncher
import app.lawnchair.util.requestContactPermissionGranted
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BaseModelUpdateTask
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio

class LawnchairLocalSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private val generateSearchTarget = GenerateSearchTarget(context)

    private lateinit var hiddenApps: Set<String>

    private var hiddenAppsInSearch = ""

    private var searchApps = true
    private var enableFuzzySearch = false
    private var useWebSuggestions = true

    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val pref2 = PreferenceManager2.getInstance(context)

    private var maxAppResultsCount = 5
    private var maxPeopleCount = 10
    private var maxWebSuggestionsCount = 3
    private var maxFilesCount = 3
    private var maxSettingsEntryCount = 5
    private var maxRecentResultCount = 2
    private var maxWebSuggestionDelay = 200

    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

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

        pref2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxAppResultsCount = it
        }
        pref2.maxFileResultCount.onEach(launchIn = coroutineScope) {
            maxFilesCount = it
        }
        pref2.maxPeopleResultCount.onEach(launchIn = coroutineScope) {
            maxPeopleCount = it
        }
        pref2.maxSuggestionResultCount.onEach(launchIn = coroutineScope) {
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
        appState.model.enqueueModelUpdateTask(object : BaseModelUpdateTask() {
            override fun execute(app: LauncherAppState, dataModel: BgDataModel, apps: AllAppsList) {
                coroutineScope.launch(Dispatchers.Main) {
                    val results = getResult(apps.data, query)
                    callback.onSearchResult(query, results)
                }
            }
        })
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    private suspend fun getResult(
        apps: MutableList<AppInfo>,
        query: String,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val appResults = if (enableFuzzySearch) {
            fuzzySearch(apps, query)
        } else {
            normalSearch(apps, query)
        }

        val localSearchResults = performDeviceLocalSearch(query, prefs)

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty() && searchApps) {
            appResults.mapTo(searchTargets, ::createSearchTarget)
        }

        if (appResults.size == 1 && searchApps && context.isDefaultLauncher()) {
            val singleAppResult = appResults.first()
            val shortcuts = getShortcuts(singleAppResult)
            if (shortcuts.isNotEmpty()) {
                searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))
                searchTargets.add(createSearchTarget(singleAppResult, true))
                searchTargets.addAll(shortcuts.map(::createSearchTarget))
            }
        }

        val suggestions = filterByType(localSearchResults, WEB_SUGGESTION)
        if (suggestions.isNotEmpty()) {
            val suggestionsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_suggestions))
            searchTargets.add(suggestionsHeader)
            searchTargets.addAll(suggestions.map { generateSearchTarget.getSuggestionTarget(it.resultData as String) })
        }

        val calculator = filterByType(localSearchResults, CALCULATOR).first()
        val calcData = calculator.resultData as Calculation
        if (calcData.isValid) {
            val calculatorHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_calculator))
            searchTargets.add(calculatorHeader)
            searchTargets.add(
                generateSearchTarget.getCalculationTarget(calcData),
            )
        }

        val contacts = filterByType(localSearchResults, CONTACT)
        if (contacts.isNotEmpty()) {
            val contactsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_contacts_from_device))
            searchTargets.add(contactsHeader)
            searchTargets.addAll(contacts.map { generateSearchTarget.getContactSearchItem(it.resultData as ContactInfo) })
        }

        val settings = filterByType(localSearchResults, SETTINGS)
        if (settings.isNotEmpty()) {
            val settingsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_settings_entry_from_device))
            searchTargets.add(settingsHeader)
            searchTargets.addAll(settings.mapNotNull { generateSearchTarget.getSettingSearchItem(it.resultData as SettingInfo) })
        }

        val recentKeyword = filterByType(localSearchResults, HISTORY)
        if (recentKeyword.isNotEmpty()) {
            val recentKeywordHeader = generateSearchTarget.getHeaderTarget(
                context.getString(R.string.search_pref_result_history_title),
                HEADER_JUSTIFY,
            )
            searchTargets.add(recentKeywordHeader)
            searchTargets.addAll(recentKeyword.map { generateSearchTarget.getRecentKeywordTarget(it.resultData as RecentKeyword) })
        }

        val files = filterByType(localSearchResults, FILES)
        if (files.isNotEmpty()) {
            val filesHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_files))
            searchTargets.add(filesHeader)
            searchTargets.addAll(files.map { generateSearchTarget.getFileInfoSearchItem(it.resultData as IFileInfo) })
        }

        searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))

        if (useWebSuggestions) searchTargets.add(generateSearchTarget.getStartPageSearchItem(query))
        generateSearchTarget.getMarketSearchItem(query)?.let { searchTargets.add(it) }

        val adapterItems = transformSearchResults(searchTargets)
        LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems)
        return ArrayList(adapterItems)
    }

    private fun filterByType(results: List<SearchResult>, type: String): List<SearchResult> {
        return results.filter { it.resultType == type }
    }

    private fun getShortcuts(app: AppInfo): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts, null)
    }

    private fun normalSearch(apps: List<AppInfo>, query: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val queryTextLower = query.lowercase(Locale.getDefault())
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(queryTextLower, it.title.toString(), matcher) }
            .filterHiddenApps(queryTextLower)
            .take(maxAppResultsCount)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String): List<AppInfo> {
        val queryTextLower = query.lowercase(Locale.getDefault())
        val filteredApps = apps.asSequence()
            .filterHiddenApps(queryTextLower)
            .toList()
        val matches = FuzzySearch.extractSorted(
            queryTextLower,
            filteredApps,
            { it.sectionName + it.title },
            WeightedRatio(),
            65,
        )

        return matches.take(maxAppResultsCount)
            .map { it.referent }
    }

    private fun Sequence<AppInfo>.filterHiddenApps(query: String): Sequence<AppInfo> {
        return when (hiddenAppsInSearch) {
            HiddenAppsInSearch.ALWAYS -> {
                this
            }
            HiddenAppsInSearch.IF_NAME_TYPED -> {
                filter {
                    it.toComponentKey().toString() !in hiddenApps ||
                        it.title.toString().lowercase(Locale.getDefault()) == query
                }
            }
            else -> {
                filter { it.toComponentKey().toString() !in hiddenApps }
            }
        }
    }

    protected suspend fun performDeviceLocalSearch(query: String, prefs: PreferenceManager): MutableList<SearchResult> =
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
                findSettingsByNameAndAction(query, maxSettingsEntryCount)
                    .map { SearchResult(SETTINGS, it) }
            }

            val startPageSuggestionsDeferred = async {
                try {
                    val timeout = maxWebSuggestionDelay.toLong()
                    val result = withTimeoutOrNull(timeout) {
                        if (prefs.searchResultStartPageSuggestion.get()) {
                            getStartPageSuggestions(query, maxWebSuggestionsCount).map {
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
}
