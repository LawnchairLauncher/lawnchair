package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.GenerateSearchTarget
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.createSearchTarget
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BaseModelUpdateTask
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private val generateSearchTarget = GenerateSearchTarget(context)

    private var hiddenApps: Set<String> = setOf()

    private var hiddenAppsInSearch = ""
    private var enableFuzzySearch = false
    private var maxResultsCount = 5

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

    private val searchUtils = SearchUtils(maxResultsCount, hiddenApps, hiddenAppsInSearch)

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
            searchUtils.fuzzySearch(apps, query)
        } else {
            searchUtils.normalSearch(apps, query)
        }

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { searchUtils.getShortcuts(it, context) }
                if (shortcuts != null) {
                    if (shortcuts.isNotEmpty()) {
                        searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))
                        searchTargets.add(createSearchTarget(singleAppResult, true))
                        searchTargets.addAll(shortcuts.map(::createSearchTarget))
                    }
                }
            } else {
                appResults.mapTo(searchTargets, ::createSearchTarget)
            }
        }

        searchTargets.add(generateSearchTarget.getHeaderTarget(SPACE))

        generateSearchTarget.getMarketSearchItem(query)?.let { searchTargets.add(it) }

        setFirstItemQuickLaunch(searchTargets)
        val adapterItems = transformSearchResults(searchTargets)
        return ArrayList(adapterItems)
    }
}
