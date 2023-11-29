package app.lawnchair.search

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.SearchItemBackground
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.SearchTargetCompat.Companion.RESULT_TYPE_APPLICATION
import app.lawnchair.search.SearchTargetCompat.Companion.RESULT_TYPE_SHORTCUT
import app.lawnchair.search.data.SearchResult
import app.lawnchair.search.data.findByFileName
import app.lawnchair.search.data.findContactByName
import app.lawnchair.search.data.getStartPageSuggestions
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.contactPermissionGranted
import com.android.app.search.LayoutType.EMPTY_DIVIDER
import com.android.app.search.LayoutType.HORIZONTAL_MEDIUM_TEXT
import com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT
import com.android.app.search.LayoutType.PEOPLE_TILE
import com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.TEXT_HEADER
import com.android.app.search.LayoutType.THUMBNAIL
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class LawnchairSearchAlgorithm(
    protected val context: Context,
) : SearchAlgorithm<BaseAllAppsAdapter.AdapterItem> {

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

        val smallIconIndices = findIndices(filtered, SMALL_ICON_HORIZONTAL_TEXT)
        val iconRowIndices = findIndices(filtered, ICON_HORIZONTAL_TEXT)
        val peopleTileIndices = findIndices(filtered, PEOPLE_TILE)
        val suggestionIndices = findIndices(filtered, HORIZONTAL_MEDIUM_TEXT)
        val fileIndices = findIndices(filtered, THUMBNAIL)

        return filtered.mapIndexedNotNull { index, target ->
            val isFirst = index == 0 || filtered[index - 1].isDivider
            val isLast = index == filtered.lastIndex || filtered[index + 1].isDivider
            val background = getBackground(
                target.layoutType, index, isFirst, isLast,
                smallIconIndices, iconRowIndices, peopleTileIndices, suggestionIndices, fileIndices,
            )
            SearchAdapterItem.createAdapterItem(target, background)
        }
    }

    private fun findIndices(filtered: List<SearchTargetCompat>, layoutType: String): List<Int> {
        return filtered.indices.filter {
            filtered[it].layoutType == layoutType
        }
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
    ): SearchItemBackground = when {
        layoutType == TEXT_HEADER || layoutType == ICON_SINGLE_VERTICAL_TEXT || layoutType == EMPTY_DIVIDER -> iconBackground
        layoutType == SMALL_ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, smallIconIndices)
        layoutType == ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, iconRowIndices)
        layoutType == PEOPLE_TILE -> getGroupedBackground(index, peopleTileIndices)
        layoutType == HORIZONTAL_MEDIUM_TEXT -> getGroupedBackground(index, suggestionIndices)
        layoutType == THUMBNAIL -> getGroupedBackground(index, fileIndices)
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

    protected suspend fun performDeviceWideSearch(query: String, prefs: PreferenceManager): MutableList<SearchResult> = withContext(Dispatchers.IO) {
        val results = ArrayList<SearchResult>()

        if (prefs.searchResultPeople.get() && contactPermissionGranted(context, prefs)) {
            val contactResults = findContactByName(context, query, 10)
            results.addAll(contactResults.map { SearchResult(CONTACT, it) })
        }

        if (prefs.searchResultFiles.get() && checkAndRequestFilesPermission(context, prefs)) {
            val fileResults = findByFileName(context, query, 2)
            results.addAll(fileResults.map { SearchResult(FILES, it) })
        }

        if (prefs.searchResultStartPageSuggestion.get()) {
            val suggestionsResults = getStartPageSuggestions(query, 3)
            results.addAll(suggestionsResults.map { SearchResult(SUGGESTION, it) })
        }

        results
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
