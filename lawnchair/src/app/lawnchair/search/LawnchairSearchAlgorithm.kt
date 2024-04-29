package app.lawnchair.search

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.SearchTargetCompat.Companion.RESULT_TYPE_APPLICATION
import app.lawnchair.search.SearchTargetCompat.Companion.RESULT_TYPE_SHORTCUT
import app.lawnchair.search.data.SearchCallback
import app.lawnchair.search.data.SearchResult
import app.lawnchair.search.data.findContactsByName
import app.lawnchair.search.data.findSettingsByNameAndAction
import app.lawnchair.search.data.getRecentKeyword
import app.lawnchair.search.data.getStartPageSuggestions
import app.lawnchair.search.data.queryFilesInMediaStore
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.requestContactPermissionGranted
import com.android.app.search.LayoutType.EMPTY_DIVIDER
import com.android.app.search.LayoutType.HORIZONTAL_MEDIUM_TEXT
import com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT
import com.android.app.search.LayoutType.ICON_SLICE
import com.android.app.search.LayoutType.PEOPLE_TILE
import com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT
import com.android.app.search.LayoutType.TEXT_HEADER
import com.android.app.search.LayoutType.THUMBNAIL
import com.android.app.search.LayoutType.WIDGET_LIVE
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchAlgorithm
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        val settingIndices = findIndices(filtered, ICON_SLICE)
        val recentIndices = findIndices(filtered, WIDGET_LIVE)

        return filtered.mapIndexedNotNull { index, target ->
            val isFirst = index == 0 || filtered[index - 1].isDivider
            val isLast = index == filtered.lastIndex || filtered[index + 1].isDivider
            val background = getBackground(
                target.layoutType, index, isFirst, isLast,
                smallIconIndices, iconRowIndices, peopleTileIndices, suggestionIndices, fileIndices, settingIndices, recentIndices,
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
        settingIndices: List<Int>,
        recentIndices: List<Int>,
    ): SearchItemBackground = when {
        layoutType == TEXT_HEADER || layoutType == ICON_SINGLE_VERTICAL_TEXT || layoutType == EMPTY_DIVIDER -> iconBackground
        layoutType == SMALL_ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, smallIconIndices)
        layoutType == ICON_HORIZONTAL_TEXT -> getGroupedBackground(index, iconRowIndices)
        layoutType == PEOPLE_TILE -> getGroupedBackground(index, peopleTileIndices)
        layoutType == HORIZONTAL_MEDIUM_TEXT -> getGroupedBackground(index, suggestionIndices)
        layoutType == THUMBNAIL -> getGroupedBackground(index, fileIndices)
        layoutType == ICON_SLICE -> getGroupedBackground(index, settingIndices)
        layoutType == WIDGET_LIVE -> getGroupedBackground(index, recentIndices)
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

    private var maxPeopleCount = 10
    private var maxSuggestionCount = 3
    private var maxFilesCount = 3
    private var maxSettingsEntryCount = 5
    private var maxRecentResultCount = 2
    private var maxWebSuggestionDelay = 200
    val coroutineScope = CoroutineScope(context = Dispatchers.IO)
    val pref2 = PreferenceManager2.getInstance(context)

    init {
        pref2.maxFileResultCount.onEach(launchIn = coroutineScope) {
            maxFilesCount = it
        }
        pref2.maxPeopleResultCount.onEach(launchIn = coroutineScope) {
            maxPeopleCount = it
        }
        pref2.maxSuggestionResultCount.onEach(launchIn = coroutineScope) {
            maxSuggestionCount = it
        }
        pref2.maxSettingsEntryResultCount.onEach(launchIn = coroutineScope) {
            maxSettingsEntryCount = it
        }
        pref2.maxRecentResultCount.onEach(launchIn = coroutineScope) {
            maxRecentResultCount = it
        }
        pref2.maxWebSuggestionDelay.onEach(launchIn = coroutineScope) {
            maxWebSuggestionDelay = it
        }
    }

    protected suspend fun performDeviceWideSearch(query: String, prefs: PreferenceManager): MutableList<SearchResult> = withContext(Dispatchers.IO) {
        val results = ArrayList<SearchResult>()

        val contactDeferred = async {
            if (prefs.searchResultPeople.get() && requestContactPermissionGranted(context, prefs)) {
                findContactsByName(context, query, maxPeopleCount)
                    .map { SearchResult(CONTACT, it) }
            } else {
                emptyList()
            }
        }

        val filesDeferred = async {
            if (prefs.searchResultFiles.get() && checkAndRequestFilesPermission(context, prefs)) {
                queryFilesInMediaStore(context, keyword = query, maxResult = maxFilesCount)
                    .toList()
                    .map { SearchResult(FILES, it) }
            } else {
                emptyList()
            }
        }

        val settingsDeferred = async {
            if (prefs.searchResultSettings.get()) {
                findSettingsByNameAndAction(query, maxSettingsEntryCount)
                    .map { SearchResult(SETTING, it) }
            } else {
                emptyList()
            }
        }

        val startPageSuggestionsDeferred = async {
            try {
                val timeout = maxWebSuggestionDelay.toLong()
                val result = withTimeoutOrNull(timeout) {
                    if (prefs.searchResultStartPageSuggestion.get()) {
                        getStartPageSuggestions(query, maxSuggestionCount).map { SearchResult(SUGGESTION, it) }
                    } else {
                        emptyList()
                    }
                }
                result ?: emptyList()
            } catch (e: TimeoutCancellationException) {
                emptyList()
            }
        }

        if (prefs.searchResulRecentSuggestion.get()) {
            getRecentKeyword(
                context,
                query,
                maxRecentResultCount,
                object : SearchCallback {
                    override fun onSearchLoaded(items: List<Any>) {
                        results.addAll(items.map { SearchResult(RECENT_KEYWORD, it) })
                    }

                    override fun onSearchFailed(error: String) {
                        results.add(SearchResult(ERROR, error))
                    }

                    override fun onLoading() {
                        results.add(SearchResult(LOADING, "Loading"))
                    }
                },
            )
        }

        results.addAll(contactDeferred.await())
        results.addAll(filesDeferred.await())
        results.addAll(settingsDeferred.await())
        results.addAll(startPageSuggestionsDeferred.await())

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
