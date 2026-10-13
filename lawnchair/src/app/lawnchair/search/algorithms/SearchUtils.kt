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
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val queryNormalized = normalizeForSearch(query)
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { matchesAppTitle(queryNormalized, it.title.toString(), matcher) }
            .filterHiddenApps(queryNormalized, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
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

    /**
     * Returns whether [title] matches [query] in the non-fuzzy search.
     *
     * The query must already be normalized with [normalizeForSearch]. The title is normalized
     * here, keeping its case so that camel case word breaks still work (e.g. "tube" matches
     * "YouTube").
     *
     * The query has to match at the start of a word. Arabic labels often start with an attached
     * article or prefix (e.g. "al-"), so for Arabic queries any substring of the title also
     * matches, similar to how [StringMatcherUtility] handles Han characters.
     */
    fun matchesAppTitle(query: String, title: String, matcher: StringMatcherUtility.StringMatcher): Boolean {
        val titleNormalized = normalizeForSearch(title, lowercase = false)
        return StringMatcherUtility.matches(query, titleNormalized, matcher) ||
            (query.containsArabicScript() && titleNormalized.lowercase(Locale.getDefault()).contains(query))
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
