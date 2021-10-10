package app.lawnchair.search

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Process
import app.lawnchair.allapps.SearchItemBackground
import app.lawnchair.allapps.SearchResultView
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import java.util.*
import kotlin.collections.ArrayList

class LawnchairAppSearchAlgorithm(private val context: Context) :
    DefaultAppSearchAlgorithm(context) {

    private val useFuzzySearch by PreferenceManager.getInstance(context).useFuzzySearch
    private val iconBackground = SearchItemBackground(
        context,
        showBackground = false,
        roundTop = true,
        roundBottom = true
    )
    private val normalBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = true,
        roundBottom = true
    )
    private val marketSearchComponent = resolveMarketSearchActivity()

    override fun getResult(
        apps: MutableList<AppInfo>,
        query: String
    ): ArrayList<AdapterItem> {
        val results = if (useFuzzySearch) {
            fuzzySearch(apps, query)
        } else {
            normalSearch(apps, query)
        }
        if (results.isEmpty()) {
            results.add(getEmptySearchItem(query))
        }
        return ArrayList(LawnchairSearchAdapterProvider.decorateSearchResults(results))
    }

    private fun normalSearch(apps: List<AppInfo>, query: String): MutableList<SearchAdapterItem> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val queryTextLower = query.lowercase(Locale.getDefault())
        val matcher = StringMatcherUtility.StringMatcher.getInstance()

        return apps.asSequence()
            .filter { StringMatcherUtility.matches(queryTextLower, it.title.toString(), matcher) }
            .take(MAX_RESULTS_COUNT)
            .mapIndexed { index, info ->
                SearchAdapterItem.fromApp(index, info, iconBackground)
            }
            .toCollection(ArrayList())
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String): MutableList<SearchAdapterItem> {
        val matches = FuzzySearch.extractSorted(
            query.lowercase(Locale.getDefault()), apps,
            { it.title.toString() }, WeightedRatio(), 65
        )

        return matches.take(MAX_RESULTS_COUNT)
            .mapIndexed { index, match ->
                SearchAdapterItem.fromApp(index, match.referent, iconBackground)
            }
            .toCollection(ArrayList())
    }

    private fun resolveMarketSearchActivity(): ComponentKey? {
        val intent = PackageManagerHelper.getMarketSearchIntent(context, "")
        val resolveInfo = context.packageManager.resolveActivity(intent, 0) ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return ComponentKey(launchIntent.component, Process.myUserHandle())
    }

    private fun getEmptySearchItem(query: String): SearchAdapterItem {
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
        return SearchAdapterItem.fromAction(0, id, action, normalBackground, extras)
    }
}
