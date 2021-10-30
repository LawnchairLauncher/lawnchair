package app.lawnchair.search

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.SearchItemBackground
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.SearchTargetCompat.RESULT_TYPE_APPLICATION
import app.lawnchair.search.SearchTargetCompat.RESULT_TYPE_SHORTCUT
import com.android.app.search.LayoutType
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

    protected fun transformSearchResults(results: List<SearchTargetCompat>): List<SearchAdapterItem> {
        val tipsEnabled = PreferenceManager.getInstance(context).searchResultPixelTips.get()
        val filtered = results
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .filterNot { it.packageName == PACKAGE_SETTINGS && !it.isApp && !it.isShortcut }
            .filterNot { !tipsEnabled && it.packageName == PACKAGE_TIPS && !it.isApp }
            .filter { LawnchairSearchAdapterProvider.viewTypeMap[it.layoutType] != null }
            .removeDuplicateDividers()
        return filtered
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
    }

    companion object {
        private const val PACKAGE_SETTINGS = "com.android.settings"
        private const val PACKAGE_TIPS = "com.google.android.apps.tips"

        fun isDeviceSearchEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getInstance(context)
            return prefs.deviceSearch.get() && Utilities.ATLEAST_S && LawnchairApp.isRecentsEnabled
        }

        fun create(context: Context): LawnchairSearchAlgorithm = when {
            isDeviceSearchEnabled(context) -> LawnchairDeviceSearchAlgorithm(context)
            else -> LawnchairAppSearchAlgorithm(context)
        }
    }
}

private fun Iterable<SearchTargetCompat>.removeDuplicateDividers(): List<SearchTargetCompat> {
    var previousWasDivider = true
    return filter { item ->
        val isDivider = item.layoutType == EMPTY_DIVIDER
        val remove = isDivider && previousWasDivider
        previousWasDivider = isDivider
        !remove
    }
}

private val SearchTargetCompat.isApp get() = resultType == RESULT_TYPE_APPLICATION
private val SearchTargetCompat.isShortcut get() = resultType == RESULT_TYPE_SHORTCUT
private val SearchTargetCompat.isDivider get() = layoutType == EMPTY_DIVIDER
