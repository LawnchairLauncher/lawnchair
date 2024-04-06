package app.lawnchair.search

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Handler
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.data.ContactInfo
import app.lawnchair.search.data.IFileInfo
import app.lawnchair.search.data.RecentKeyword
import app.lawnchair.search.data.SearchResult
import app.lawnchair.search.data.SettingInfo
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private var enableFuzzySearch = false
    private var maxResultsCount = 5
    private lateinit var hiddenApps: Set<String>
    private var hiddenAppsInSearch = ""
    private val generateSearchTarget = GenerateSearchTarget(context)
    private var enableWideSearch = false

    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)

    init {
        pref2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        pref2.maxSearchResultCount.onEach(launchIn = coroutineScope) {
            maxResultsCount = it
        }
        pref2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        pref2.hiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            hiddenAppsInSearch = it
        }
        pref2.performWideSearch.onEach(launchIn = coroutineScope) {
            enableWideSearch = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<AdapterItem>) {
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
    ): ArrayList<AdapterItem> {
        val appResults = if (enableFuzzySearch) {
            fuzzySearch(apps, query)
        } else {
            normalSearch(apps, query)
        }

        val wideSearchResults = if (enableWideSearch) performDeviceWideSearch(query, prefs) else emptyList()

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            appResults.mapTo(searchTargets, ::createSearchTarget)
        }

        if (appResults.size == 1 && context.isDefaultLauncher()) {
            val singleAppResult = appResults.first()
            val shortcuts = getShortcuts(singleAppResult)
            if (shortcuts.isNotEmpty()) {
                searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))
                searchTargets.add(createSearchTarget(singleAppResult, true))
                searchTargets.addAll(shortcuts.map(::createSearchTarget))
            }
        }

        val contacts = filterByType(wideSearchResults, CONTACT)
        if (contacts.isNotEmpty()) {
            val contactsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_contacts_from_device))
            searchTargets.add(contactsHeader)
            searchTargets.addAll(contacts.map { generateSearchTarget.getContactSearchItem(it.resultData as ContactInfo) })
        }

        val settings = filterByType(wideSearchResults, SETTING)
        if (settings.isNotEmpty()) {
            val settingsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_settings_entry_from_device))
            searchTargets.add(settingsHeader)
            searchTargets.addAll(settings.mapNotNull { generateSearchTarget.getSettingSearchItem(it.resultData as SettingInfo) })
        }

        val recentKeyword = filterByType(wideSearchResults, RECENT_KEYWORD)
        if (recentKeyword.isNotEmpty()) {
            val recentKeywordHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.search_pref_result_history_title), HEADER_JUSTIFY)
            searchTargets.add(recentKeywordHeader)
            searchTargets.addAll(recentKeyword.map { generateSearchTarget.getRecentKeywordTarget(it.resultData as RecentKeyword) })
        }

        val suggestions = filterByType(wideSearchResults, SUGGESTION)
        if (suggestions.isNotEmpty()) {
            val suggestionsHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_suggestions))
            searchTargets.add(suggestionsHeader)
            searchTargets.addAll(suggestions.map { generateSearchTarget.getSuggestionTarget(it.resultData as String) })
        }

        val files = filterByType(wideSearchResults, FILES)
        if (files.isNotEmpty()) {
            val filesHeader = generateSearchTarget.getHeaderTarget(context.getString(R.string.all_apps_search_result_files))
            searchTargets.add(filesHeader)
            searchTargets.addAll(files.map { generateSearchTarget.getFileInfoSearchItem(it.resultData as IFileInfo) })
        }

        searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))

        searchTargets.add(generateSearchTarget.getStartPageSearchItem(query))
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
            .take(maxResultsCount)
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

        return matches.take(maxResultsCount)
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
}
