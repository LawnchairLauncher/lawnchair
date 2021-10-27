package app.lawnchair.search

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.SearchItemBackground
import com.android.app.search.LayoutType.*
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.search.SearchAlgorithm

abstract class LawnchairSearchAlgorithm(
    val context: Context
) : SearchAlgorithm<AllAppsGridAdapter.AdapterItem> {

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

    protected fun transformSearchResults(results: List<SearchTargetCompat>): ArrayList<AllAppsGridAdapter.AdapterItem> {
        val filtered = results.filter { it.packageName != BuildConfig.APPLICATION_ID }
        val items = filtered
            .mapIndexedNotNull { index, target ->
                val isFirst = index == 0 || filtered[index - 1].isDivider
                val isLast = index == filtered.lastIndex || filtered[index + 1].isDivider
                val background = when {
                    target.layoutType == ICON_SINGLE_VERTICAL_TEXT -> iconBackground
                    target.layoutType == EMPTY_DIVIDER -> iconBackground
                    isFirst && isLast -> normalBackground
                    isFirst -> topBackground
                    isLast -> bottomBackground
                    else -> centerBackground
                }
                SearchAdapterItem.createAdapterItem(index, target, background)
            }
        return ArrayList(LawnchairSearchAdapterProvider.decorateSearchResults(items))
    }

    companion object {
        fun create(context: Context) = when {
            Utilities.ATLEAST_S && LawnchairApp.isRecentsEnabled -> LawnchairDeviceSearchAlgorithm(context)
            else -> LawnchairAppSearchAlgorithm(context)
        }
    }
}

private val SearchTargetCompat.isDivider get() = layoutType == EMPTY_DIVIDER
