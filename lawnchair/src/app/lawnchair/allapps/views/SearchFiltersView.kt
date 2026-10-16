package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.lawnchair.allapps.AllAppsSearchInput
import app.lawnchair.launcher
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.algorithms.SearchScope

class SearchFiltersView(context: Context, attrs: AttributeSet?) :
    HorizontalScrollView(context, attrs),
    SearchResultView {
    override val isQuickLaunch = false
    override fun launch() = false
    private val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    init {
        isHorizontalScrollBarEnabled = false
        addView(bar)
    }
    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        val input = context.launcher.appsView.searchUiManager as? AllAppsSearchInput ?: return
        bar.removeAllViews()
        SearchScope.entries.filter { input.supportsLocalFilters || it in setOf(SearchScope.ALL, SearchScope.APPS, SearchScope.ACTIONS) }.forEach { scope ->
            bar.addView(
                Button(context).apply {
                    text = context.getString(scope.label)
                    isSelected = target.extras.getString("scope") == scope.name
                    alpha = if (isSelected) 1f else .65f
                    setOnClickListener { input.filterSearch(scope) }
                },
            )
        }
    }
}
