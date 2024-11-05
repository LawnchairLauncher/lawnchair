package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
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
import kotlinx.coroutines.launch

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
        appState.model.enqueueModelUpdateTask(object : LauncherModel.ModelUpdateTask {
            override fun execute(app: ModelTaskController, dataModel: BgDataModel, apps: AllAppsList) {
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
            SearchUtils.fuzzySearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        } else {
            SearchUtils.normalSearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        }

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)

            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { SearchUtils.getShortcuts(it, context) }
                if (shortcuts != null) {
                    if (shortcuts.isNotEmpty()) {
                        searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
                        singleAppResult.let { searchTargets.add(searchTargetFactory.createAppSearchTarget(it, true)) }
                        searchTargets.addAll(shortcuts.map(searchTargetFactory::createShortcutTarget))
                    }
                }
            }
            searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        }

        searchTargetFactory.createMarketSearchTarget(query)?.let { searchTargets.add(it) }

        setFirstItemQuickLaunch(searchTargets)
        val adapterItems = transformSearchResults(searchTargets)
        return ArrayList(adapterItems)
    }
}
