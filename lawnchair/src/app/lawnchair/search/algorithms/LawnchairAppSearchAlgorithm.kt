package app.lawnchair.search.algorithms

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Handler
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.search.adapter.GenerateSearchTarget
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.createSearchTarget
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import java.util.Locale

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private val generateSearchTarget = GenerateSearchTarget(context)

    private lateinit var hiddenApps: Set<String>

    private var hiddenAppsInSearch = ""
    private var enableFuzzySearch = false
    private var maxResultsCount = 5

    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val pref2 = PreferenceManager2.getInstance(context)

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
        pref2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxResultsCount = it
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

    private fun getResult(
        apps: MutableList<AppInfo>,
        query: String,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val appResults = if (enableFuzzySearch) {
            fuzzySearch(apps, query)
        } else {
            normalSearch(apps, query)
        }

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

        searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))

        generateSearchTarget.getMarketSearchItem(query)?.let { searchTargets.add(it) }

        val adapterItems = transformSearchResults(searchTargets)
        LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems)
        return ArrayList(adapterItems)
    }

    private fun getShortcuts(app: AppInfo): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts)
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
