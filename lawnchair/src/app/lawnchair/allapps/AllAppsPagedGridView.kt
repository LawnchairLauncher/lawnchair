package app.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.views.ActivityContext

/**
 * "Paged drawer" rendering: a swipeable set of fixed columns x rows grids,
 * as an alternative to the default single-scrolling AllAppsRecyclerView.
 * Each page is a plain non-scrolling grid — no RecyclerView/adapter
 * machinery needed since page contents are small and fixed-size. No page
 * indicator is wired up (this app never sets app:pageIndicator), so no tab
 * header/dots ever show.
 */
class AllAppsPagedGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PagedView<PageIndicatorDots>(context, attrs) {

    private val activityContext = ActivityContext.lookupContext<Launcher>(context)
    private val prefs2 = PreferenceManager2.getInstance(context)

    /** Fires on every scroll change (drag or fling) with a 0..1 progress across all pages. */
    var onScrollProgressChanged: ((Float) -> Unit)? = null

    override fun isInfiniteScrollEnabled(): Boolean = prefs2.drawerInfiniteSwipe.firstCached()

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollProgressChanged?.invoke(currentScrollProgress())
    }

    /** Mirrors Workspace/WallpaperOffsetInterpolator's scroll-fraction-across-N-pages math. */
    fun currentScrollProgress(): Float {
        if (childCount <= 1) return 0f
        val startScroll = getScrollForPage(0)
        val endScroll = getScrollForPage(childCount - 1)
        val range = endScroll - startScroll
        if (range == 0) return 0f
        return ((scrollX - startScroll).toFloat() / range).coerceIn(0f, 1f)
    }

    /**
     * Rebuilds all pages from scratch. [items] should already be in the
     * desired display order (alphabetical) and filtered to icon-type
     * AdapterItems only — section headers/dividers aren't real apps and
     * would throw off page-size math.
     */
    fun setApps(items: List<BaseAllAppsAdapter.AdapterItem>, columns: Int, rows: Int) {
        removeAllViews()
        val pageSize = (columns * rows).coerceAtLeast(1)
        val inflater = LayoutInflater.from(context)
        fun newCellParams() = GridLayout.LayoutParams().apply {
            width = 0
            height = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        items.chunked(pageSize).forEach { chunk ->
            val page = GridLayout(context).apply {
                columnCount = columns
                rowCount = rows
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            chunk.forEach { item ->
                val icon = inflater.inflate(R.layout.all_apps_icon, page, false) as BubbleTextView
                icon.applyFromApplicationInfo(item.itemInfo)
                icon.setOnClickListener(activityContext.itemOnClickListener)
                icon.setOnLongClickListener(activityContext.allAppsItemLongClickListener)
                page.addView(icon, newCellParams())
            }
            // Pad a partial (usually last) page with invisible filler cells
            // so every page has the same full columns x rows child count —
            // GridLayout centers sparse content instead of anchoring it to
            // the top-left, so a genuinely full grid is what keeps a partial
            // page's icons aligned the same way as a full one.
            repeat(pageSize - chunk.size) {
                page.addView(View(context), newCellParams())
            }
            addView(page)
        }
        if (getNextPage() >= childCount) {
            setCurrentPage((childCount - 1).coerceAtLeast(0))
        }
    }
}
