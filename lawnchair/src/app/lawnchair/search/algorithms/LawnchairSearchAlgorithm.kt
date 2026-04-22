package app.lawnchair.search.algorithms

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.allapps.views.SearchResultView.Companion.EXTRA_QUICK_LAUNCH
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.search.adapter.START_PAGE
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetCompat.Companion.RESULT_TYPE_APPLICATION
import app.lawnchair.search.adapter.SearchTargetCompat.Companion.RESULT_TYPE_SHORTCUT
import com.android.app.search.LayoutType.CALCULATOR
import com.android.app.search.LayoutType.EDUCARD
import com.android.app.search.LayoutType.EMPTY_DIVIDER
import com.android.app.search.LayoutType.EMPTY_STATE
import com.android.app.search.LayoutType.HORIZONTAL_MEDIUM_TEXT
import com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT
import com.android.app.search.LayoutType.ICON_SLICE
import com.android.app.search.LayoutType.PEOPLE_TILE
import com.android.app.search.LayoutType.PLAY_PLACEHOLDER
import com.android.app.search.LayoutType.SEARCH_SETTINGS
import com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.TEXT_HEADER
import com.android.app.search.LayoutType.THUMBNAIL
import com.android.app.search.LayoutType.WIDGET_LIVE
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchAlgorithm
import com.android.launcher3.search.SearchCallback
import com.patrykmichalik.opto.core.firstBlocking

sealed class LawnchairSearchAlgorithm(
    protected val context: Context,
) : SearchAlgorithm<BaseAllAppsAdapter.AdapterItem> {

    private val transparentBackground = SearchItemBackground(
        context,
        showBackground = false,
        roundBottom = false,
        roundTop = false,
    )
    private val iconBackground = SearchItemBackground(
        context,
        showBackground = false,
        roundTop = true,
        roundBottom = true,
    )
    private val normalBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = true,
        roundBottom = true,
    )
    private val topBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = true,
        roundBottom = false,
    )
    private val centerBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = false,
        roundBottom = false,
    )
    private val bottomBackground = SearchItemBackground(
        context,
        showBackground = true,
        roundTop = false,
        roundBottom = true,
    )

    protected fun transformSearchResults(results: List<SearchTargetCompat>): List<SearchAdapterItem> {
        val filtered = results
            .asSequence()
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .filter { LawnchairSearchAdapterProvider.viewTypeMap[it.layoutType] != null }
            .removeDuplicateDividers()
            .toList()

        val appAndShortcutIndices = findAppAndShorcutIndices(filtered)
        val smallIconIndices = findIndices(filtered, SMALL_ICON_HORIZONTAL_TEXT)
        val iconRowIndices = findIndices(filtered, ICON_HORIZONTAL_TEXT)
        val peopleTileIndices = findIndices(filtered, PEOPLE_TILE)
        val suggestionIndices = findIndices(filtered, HORIZONTAL_MEDIUM_TEXT)
        val fileIndices = findIndices(filtered, THUMBNAIL)
        val settingIndices = findIndices(filtered, ICON_SLICE)
        val recentIndices = findIndices(filtered, WIDGET_LIVE)
        val calculator = findIndices(filtered, CALCULATOR)

        return filtered.mapIndexedNotNull { index, target ->
            val isFirst = index == 0 || filtered[index - 1].isDivider
            val isLast = index == filtered.lastIndex || filtered[index + 1].isDivider

            // todo make quick launch work on non-app results
            if (
                (target.isApp && target.layoutType == SMALL_ICON_HORIZONTAL_TEXT) ||
                target.isShortcut
            ) {
                SearchAdapterItem.createAdapterItem(target, getGroupedBackground(index, appAndShortcutIndices))
            } else if (target.layoutType == ICON_SINGLE_VERTICAL_TEXT && target.extras.getBoolean(EXTRA_QUICK_LAUNCH, false)) {
                SearchAdapterItem.createAdapterItem(target, normalBackground)
            } else {
                val background = getBackground(
                    target.layoutType,
                    index,
                    isFirst,
                    isLast,
                    smallIconIndices,
                    iconRowIndices,
                    peopleTileIndices,
                    suggestionIndices,
                    fileIndices,
                    settingIndices,
                    recentIndices,
                    calculator,
                )
                SearchAdapterItem.createAdapterItem(target, background)
            }
        }
    }

    protected fun setFirstItemQuickLaunch(searchTargets: List<SearchTargetCompat>) {
        val hasQuickLaunch = searchTargets.any { it.extras.getBoolean(EXTRA_QUICK_LAUNCH, false) }
        if (!hasQuickLaunch) {
            // check if we have a header or spacer item. if so, we skip as there isn't any relevant
            // action to be applied
            val target = searchTargets.getOrNull(
                searchTargets.indexOfFirst { it.layoutType != TEXT_HEADER },
            )
            target?.extras?.apply {
                putBoolean(EXTRA_QUICK_LAUNCH, true)
            }
        }
    }

    private fun findIndices(filtered: List<SearchTargetCompat>, layoutType: String): List<Int> {
        return filtered.indices.filter {
            filtered[it].layoutType == layoutType && !filtered[it].isApp
        }
    }

    private fun findAppAndShorcutIndices(filtered: List<SearchTargetCompat>): List<Int> {
        val appAndShortcutIndices =
            filtered.indices.filter {
                (filtered[it].isApp || filtered[it].isShortcut) &&
                    (
                        filtered[it].layoutType == ICON_HORIZONTAL_TEXT ||
                            filtered[it].layoutType == SMALL_ICON_HORIZONTAL_TEXT
                        )
            }

        return appAndShortcutIndices
    }

    private fun getBackground(
        layoutType: String,
        index: Int,
        isFirst: Boolean,
        isLast: Boolean,
        smallIconIndices: List<Int>,
        iconRowIndices: List<Int>,
        peopleTileIndices: List<Int>,
        suggestionIndices: List<Int>,
        fileIndices: List<Int>,
        settingIndices: List<Int>,
        recentIndices: List<Int>,
        calculator: List<Int>,
    ): SearchItemBackground = when {
        layoutType == TEXT_HEADER || layoutType == ICON_SINGLE_VERTICAL_TEXT || layoutType == EMPTY_DIVIDER -> iconBackground

        layoutType == SMALL_ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, smallIconIndices)

        layoutType == ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, iconRowIndices)

        layoutType == PEOPLE_TILE -> getGroupedBackground(index, peopleTileIndices)

        layoutType == HORIZONTAL_MEDIUM_TEXT -> getGroupedBackground(index, suggestionIndices)

        layoutType == THUMBNAIL -> getGroupedBackground(index, fileIndices)

        layoutType == ICON_SLICE -> getGroupedBackground(index, settingIndices)

        layoutType == WIDGET_LIVE -> getGroupedBackground(index, recentIndices)

        layoutType == CALCULATOR && calculator.isNotEmpty() -> normalBackground

        layoutType == EMPTY_STATE -> transparentBackground

        layoutType == SEARCH_SETTINGS -> transparentBackground

        //        layoutType == PLAY_PLACEHOLDER -> transparentBackground
//        layoutType == EDUCARD -> transparentBackground
        isFirst && isLast -> normalBackground

        isFirst -> topBackground

        isLast -> bottomBackground

        else -> centerBackground
    }

    private fun getGroupedBackground(index: Int, indices: List<Int>): SearchItemBackground = when {
        indices.size == 1 -> normalBackground
        index == indices.first() -> topBackground
        index == indices.last() -> bottomBackground
        else -> centerBackground
    }

    open fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        // Default implementation is to clear results.
        callback.clearSearchResult()
    }

    companion object {

        const val APP_SEARCH = "appSearch"
        const val LOCAL_SEARCH = "localSearch"
        const val ASI_SEARCH = "globalSearch"

        private var ranCompatibilityCheck = false

        fun isASISearchEnabled(context: Context): Boolean {
            if (!Utilities.ATLEAST_S) return false
            if (!LawnchairApp.isRecentsEnabled) return false

            if (!ranCompatibilityCheck) {
                ranCompatibilityCheck = true
                LawnchairASISearchAlgorithm.checkSearchCompatibility(context)
            }
            return true
        }

        fun create(context: Context): LawnchairSearchAlgorithm {
            val prefs = PreferenceManager2.getInstance(context)
            val searchAlgorithm = prefs.searchAlgorithm.firstBlocking()

            return when {
                searchAlgorithm == ASI_SEARCH && isASISearchEnabled(context) -> LawnchairASISearchAlgorithm(
                    context,
                )

                searchAlgorithm == LOCAL_SEARCH -> LawnchairLocalSearchAlgorithm(context)

                else -> LawnchairAppSearchAlgorithm(context)
            }
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
