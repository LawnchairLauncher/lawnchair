package app.lawnchair.allapps

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import java.util.*

class LawnchairAppSearchAlgorithm(context: Context, apps: MutableList<AppInfo>) : DefaultAppSearchAlgorithm(apps) {

    private val useFuzzySearch by PreferenceManager.getInstance(context).useFuzzySearch

    override fun getTitleMatchResult(query: String): ArrayList<ComponentKey> {
        if (!useFuzzySearch) return super.getTitleMatchResult(query)

        // Run a fuzzy search on all available titles using the Winkler-Jaro algorithm
        val result = ArrayList<ComponentKey>()
        val matches = FuzzySearch.extractSorted(
            query.lowercase(Locale.getDefault()), mApps,
            { it.title.toString() }, WeightedRatio(), 65)
        for (match in matches) {
            result.add(match.referent.toComponentKey())
        }
        return result
    }
}
