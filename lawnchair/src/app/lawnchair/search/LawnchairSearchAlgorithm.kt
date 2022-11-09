package app.lawnchair.search

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.SearchItemBackground
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.SearchTargetCompat.RESULT_TYPE_APPLICATION
import app.lawnchair.search.SearchTargetCompat.RESULT_TYPE_SHORTCUT
import com.android.app.search.LayoutType.EMPTY_DIVIDER
import com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT
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
        val filtered = results
            .asSequence()
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .filter { LawnchairSearchAdapterProvider.viewTypeMap[it.layoutType] != null }
            .removeDuplicateDividers()
            .toList()
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

        private var ranCompatibilityCheck = false

        fun isDeviceSearchEnabled(context: Context): Boolean {
            if (!Utilities.ATLEAST_S) return false
            if (!LawnchairApp.isRecentsEnabled) return false

            val prefs = PreferenceManager.getInstance(context)
            if (!ranCompatibilityCheck) {
                ranCompatibilityCheck = true
                LawnchairDeviceSearchAlgorithm.checkSearchCompatibility(context)
            }
            return prefs.deviceSearch.get()
        }

        fun create(context: Context): LawnchairSearchAlgorithm = when {
            isDeviceSearchEnabled(context) -> LawnchairDeviceSearchAlgorithm(context)
            else -> LawnchairAppSearchAlgorithm(context)
        }
    }
}

private fun Sequence<SearchTargetCompat>.removeDuplicateDividers(): Sequence<SearchTargetCompat> {
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
