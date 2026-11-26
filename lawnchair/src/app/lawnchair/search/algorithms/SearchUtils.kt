package app.lawnchair.search.algorithms

import android.content.Context
import android.content.pm.ShortcutInfo
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.shortcuts.ShortcutRequest
import java.util.Locale
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio

object SearchUtils {
    fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val queryNormalized = normalizeForSearch(query)
        return matchApps(
            apps.asSequence().filterHiddenApps(queryNormalized, hiddenApps, hiddenAppsInSearch),
            queryNormalized,
            maxResultsCount,
        )
    }

    /**
     * Returns up to [maxResultsCount] of [apps] whose title matches [query], which must already be
     * normalized with [normalizeForSearch]. Word-start matches come first, so Arabic
     * match-anywhere results never push them past the limit; otherwise the order of [apps] is kept.
     */
    fun matchApps(apps: Sequence<AppInfo>, query: String, maxResultsCount: Int): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        val (wordStart, anywhere) = apps
            .map { it to matchAppTitle(query, it.title.toString(), matcher) }
            .filter { (_, match) -> match != TitleMatch.NONE }
            .partition { (_, match) -> match == TitleMatch.WORD_START }
        return (wordStart + anywhere).take(maxResultsCount).map { (app, _) -> app }
    }

    fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val queryNormalized = normalizeForSearch(query)
        val filteredApps = apps.asSequence()
            .filterHiddenApps(queryNormalized, hiddenApps, hiddenAppsInSearch)
            .toList()
        val matches = FuzzySearch.extractSorted(
            queryNormalized,
            filteredApps,
            { normalizeForSearch(it.sectionName + it.title) },
            WeightedRatio(),
            65,
        )

        return matches.take(maxResultsCount)
            .map { it.referent }
    }

    /** How an app title matches a query in the non-fuzzy search, see [matchAppTitle]. */
    private enum class TitleMatch { NONE, WORD_START, ANYWHERE }

    /**
     * Returns how [title] matches [query] in the non-fuzzy search.
     *
     * The query must already be normalized with [normalizeForSearch]. The title is normalized
     * here, keeping its case so that camel case word breaks still work (e.g. "tube" matches
     * "YouTube").
     *
     * The query has to match at the start of a word ([TitleMatch.WORD_START]). Arabic labels
     * often start with an attached article or prefix (e.g. "al-"), so for Arabic queries any
     * substring of the title also matches ([TitleMatch.ANYWHERE]), similar to how
     * [StringMatcherUtility] handles Han characters.
     */
    private fun matchAppTitle(query: String, title: String, matcher: StringMatcherUtility.StringMatcher): TitleMatch {
        val titleNormalized = normalizeForSearch(title, lowercase = false)
        return when {
            StringMatcherUtility.matches(query, titleNormalized, matcher) -> TitleMatch.WORD_START

            query.containsArabicScript() &&
                titleNormalized.lowercase(Locale.getDefault()).contains(query) -> TitleMatch.ANYWHERE

            else -> TitleMatch.NONE
        }
    }

    fun getShortcuts(app: AppInfo, context: Context): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts)
    }
}

/**
 * Filters out hidden apps according to [hiddenAppsInSearch].
 *
 * @param query The search query, normalized with [normalizeForSearch].
 */
fun Sequence<AppInfo>.filterHiddenApps(
    query: String,
    hiddenApps: Set<String>,
    hiddenAppsInSearch: String,
): Sequence<AppInfo> {
    return when (hiddenAppsInSearch) {
        HiddenAppsInSearch.ALWAYS -> {
            this
        }

        HiddenAppsInSearch.IF_NAME_TYPED -> {
            filter {
                it.toComponentKey().toString() !in hiddenApps ||
                    normalizeForSearch(it.title.toString()) == query
            }
        }

        else -> {
            filter { it.toComponentKey().toString() !in hiddenApps }
        }
    }
}
