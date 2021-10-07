package app.lawnchair.allapps

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.model.data.AppInfo
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import java.util.*

class LawnchairAppSearchAlgorithm(context: Context) : DefaultAppSearchAlgorithm(context) {

    private val useFuzzySearch by PreferenceManager.getInstance(context).useFuzzySearch

    override fun getResult(
        apps: MutableList<AppInfo>,
        query: String
    ): ArrayList<AdapterItem> {
        if (!useFuzzySearch) return super.getResult(apps, query)

        // Run a fuzzy search on all available titles using the Winkler-Jaro algorithm
        val result = ArrayList<AdapterItem>()
        val matches = FuzzySearch.extractSorted(
            query.lowercase(Locale.getDefault()), apps,
            { it.title.toString() }, WeightedRatio(), 65
        )

        var resultCount = 0
        val total = apps.size
        for (match in matches) {
            if (resultCount == MAX_RESULTS_COUNT) break
            val info = match.referent
            val appItem = AdapterItem.asApp(resultCount, "", info, resultCount)
            result.add(appItem)
            resultCount++
        }
        return result
    }
}
