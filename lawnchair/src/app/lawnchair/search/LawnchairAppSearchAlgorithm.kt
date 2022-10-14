package app.lawnchair.search

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Process
import app.lawnchair.allapps.SearchResultView
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BaseModelUpdateTask
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PackageManagerHelper
import com.patrykmichalik.opto.core.onEach
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)
    private var enableFuzzySearch = false
    private lateinit var hiddenApps: Set<String>
    private var showHiddenAppsInSearch = false
    private var enableSmartHide = false
    private val marketSearchComponent = resolveMarketSearchActivity()
    private val coroutineScope = CoroutineScope(context = Dispatchers.IO)
    private val pref2 = PreferenceManager2.getInstance(context)

    init {
        pref2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        pref2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        pref2.showHiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            showHiddenAppsInSearch = it
        }
        pref2.enableSmartHide.onEach(launchIn = coroutineScope) {
            enableSmartHide = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<AllAppsGridAdapter.AdapterItem>) {
        appState.model.enqueueModelUpdateTask(object : BaseModelUpdateTask() {
            override fun execute(
                app: LauncherAppState?,
                dataModel: BgDataModel?,
                apps: AllAppsList?
            ) {
                val result = getResult(apps!!.data, query)
                resultHandler.post { callback.onSearchResult(query, result) }
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
        query: String
    ): ArrayList<AllAppsGridAdapter.AdapterItem> {
        val appResults = if (enableFuzzySearch) {
            fuzzySearch(apps, query)
        } else {
            normalSearch(apps, query)
        }
        val results = mutableListOf<SearchTargetCompat>()
        if (appResults.size == 1) {
            val app = appResults.first()
            val shortcuts = getShortcuts(app)
            results.add(createSearchTarget(app, true))
            shortcuts.mapTo(results, ::createSearchTarget)
        } else {
            appResults.mapTo(results, ::createSearchTarget)
        }
        if (results.isEmpty()) {
            if (marketSearchComponent == null) {
                return arrayListOf(AllAppsGridAdapter.AdapterItem.asEmptySearch(0))
            }
            results.add(getEmptySearchItem(query))
        }
        val adapterItems = transformSearchResults(results)
        LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems)
        return ArrayList(adapterItems)
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
            queryTextLower, filteredApps, { it.title.toString() }, WeightedRatio(), 65
        )

        return matches.take(maxResultsCount)
            .map { it.referent }
    }

    private fun resolveMarketSearchActivity(): ComponentKey? {
        val intent = PackageManagerHelper.getMarketSearchIntent(context, "")
        val resolveInfo = context.packageManager.resolveActivity(intent, 0) ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return ComponentKey(launchIntent.component, Process.myUserHandle())
    }

    private fun getEmptySearchItem(query: String): SearchTargetCompat {
        val id = "marketSearch:$query"
        val action = SearchActionCompat.Builder(
            id,
            context.getString(R.string.all_apps_search_market_message)
        )
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_home))
            .setIntent(PackageManagerHelper.getMarketSearchIntent(context, query))
            .build()
        val extras = Bundle().apply {
            if (marketSearchComponent != null) {
                putString(SearchResultView.EXTRA_ICON_COMPONENT_KEY, marketSearchComponent.toString())
            } else {
                putBoolean(SearchResultView.EXTRA_HIDE_ICON, true)
            }
            putBoolean(SearchResultView.EXTRA_HIDE_SUBTITLE, true)
        }
        return createSearchTarget(id, action, extras)
    }

    private fun Sequence<AppInfo>.filterHiddenApps(query: String): Sequence<AppInfo> {

        return if (showHiddenAppsInSearch) {
            this
        } else if (enableSmartHide) {
            filter {
                it.toComponentKey().toString() !in hiddenApps ||
                    it.title.toString().lowercase(Locale.getDefault()) == query
            }
        } else {
            filter { it.toComponentKey().toString() !in hiddenApps }
        }
    }

    companion object {
        private const val maxResultsCount = 5
    }
}
