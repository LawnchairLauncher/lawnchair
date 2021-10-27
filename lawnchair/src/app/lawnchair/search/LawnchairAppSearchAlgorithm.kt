package app.lawnchair.search

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Process
import app.lawnchair.allapps.SearchItemBackground
import app.lawnchair.allapps.SearchResultView
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import java.util.*

class LawnchairAppSearchAlgorithm(private val context: Context) :
    DefaultAppSearchAlgorithm(context) {

    private val useFuzzySearch by PreferenceManager.getInstance(context).useFuzzySearch
    private val iconBackground = SearchItemBackground(
        context, showBackground = false,
        roundTop = true, roundBottom = true
    )
    private val normalBackground = SearchItemBackground(
        context, showBackground = true,
        roundTop = true, roundBottom = true
    )
    private val topBackground = SearchItemBackground(
        context, showBackground = true,
        roundTop = true, roundBottom = false
    )
    private val centerBackground = SearchItemBackground(
        context, showBackground = true,
        roundTop = false, roundBottom = false
    )
    private val bottomBackground = SearchItemBackground(
        context, showBackground = true,
        roundTop = false, roundBottom = true
    )
    private val marketSearchComponent = resolveMarketSearchActivity()

    override fun getResult(
        apps: MutableList<AppInfo>,
        query: String
    ): ArrayList<AdapterItem> {
        val appResults = if (useFuzzySearch) {
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
            results.add(getEmptySearchItem(query))
        }
        val items = results
            .mapIndexed { index, target ->
                val isFirst = index == 0
                val isLast = index == results.lastIndex
                val isIcon = target.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT
                val background = when {
                    isIcon -> iconBackground
                    isFirst && isLast -> normalBackground
                    isFirst -> topBackground
                    isLast -> bottomBackground
                    else -> centerBackground
                }
                SearchAdapterItem.createAdapterItem(index, target, background)
            }
        return ArrayList(LawnchairSearchAdapterProvider.decorateSearchResults(items))
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
            .take(MAX_RESULTS_COUNT)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String): List<AppInfo> {
        val matches = FuzzySearch.extractSorted(
            query.lowercase(Locale.getDefault()), apps,
            { it.title.toString() }, WeightedRatio(), 65
        )

        return matches.take(MAX_RESULTS_COUNT)
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
}
