package app.lawnchair.fold

import android.view.View
import android.view.ViewTreeObserver
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.util.ScrollableLayoutManager
import com.android.launcher3.views.ActivityContext
import java.util.WeakHashMap

/**
 * The second page of the "book" drawer ([FoldDrawerLayout.PAGES]).
 *
 * The A-Z grid is laid out on the fixed half only ([FoldDrawerLayouts.adjustAppsGridPadding]),
 * with one more screenful of rows laid out below the visible ones. Before every frame, every child
 * that is not fully visible on the fixed page (its bottom is past the fixed page's bottom edge) is
 * moved onto the moving half, up by as many whole rows as fit on a page. So the fixed page only
 * ever shows full rows, the row it cuts is the first row of the moving page, no app is hidden, and
 * the rows of both pages line up. Both pages scroll together, by rows: when a row becomes fully
 * visible at the bottom of the fixed page it leaves the top of the moving page. The RecyclerView,
 * its fast scroller, focus and accessibility stay as they are; only view translations change, and
 * touch, drag and accessibility bounds follow them.
 *
 * One instance per A-Z RecyclerView (personal and work); it does nothing unless the current
 * profile was built for PAGES.
 */
class FoldDrawerBook private constructor(
    private val rv: AllAppsRecyclerView,
) : ViewTreeObserver.OnPreDrawListener,
    View.OnAttachStateChangeListener {

    /** Lets a transition add a vertical offset to each child while it plays. */
    fun interface OffsetProvider {
        /** [movingTop] is the child's top on the moving page (RecyclerView coordinates), if it is there. */
        fun offsetY(child: View, onMovingPage: Boolean, movingTop: Int, pageTop: Int, pageHeight: Int): Float
    }

    var offsetProvider: OffsetProvider? = null

    /** Whether the book is laid out (the current profile was built for PAGES). */
    var isActive = false
        private set

    /** Top of both pages' grids and their height, in RecyclerView coordinates, while active. */
    var pageTop = 0
        private set
    var pageHeight = 0
        private set

    private var observer: ViewTreeObserver? = null

    init {
        rv.addOnAttachStateChangeListener(this)
        if (rv.isAttachedToWindow) onViewAttachedToWindow(rv)
    }

    override fun onViewAttachedToWindow(v: View) {
        val vto = rv.viewTreeObserver
        vto.addOnPreDrawListener(this)
        observer = vto
    }

    override fun onViewDetachedFromWindow(v: View) {
        observer?.let { if (it.isAlive) it.removeOnPreDrawListener(this) }
        observer = null
    }

    override fun onPreDraw(): Boolean {
        update()
        return true
    }

    /** Places the children on their page. Safe to call any time. */
    fun update() {
        val activity: ActivityContext = ActivityContext.lookupContext(rv.context)
        val dp = activity.deviceProfile
        if (FoldDrawerLayouts.drawerLayoutOf(dp) != FoldDrawerLayout.PAGES || rv.height == 0) {
            if (isActive) deactivate()
            return
        }
        isActive = true
        val lm = rv.layoutManager ?: return
        val bottom = rv.height - rv.paddingBottom
        pageTop = rv.paddingTop
        pageHeight = (bottom - pageTop).coerceAtLeast(0)
        (lm as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(pageHeight)
        // Up by whole rows, so the cut row lands where the fixed page's first full row would be
        // and the rows of both pages line up.
        var rowHeight = 0
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            if (BaseAllAppsAdapter.isIconViewType(rv.getChildViewHolder(child).itemViewType)) {
                rowHeight = lm.getDecoratedMeasuredHeight(child)
                break
            }
        }
        val shiftY = if (rowHeight > 0) (pageHeight / rowHeight).coerceAtLeast(1) * rowHeight else pageHeight
        val shiftX = FoldDrawerLayouts.bookShiftX(dp).toFloat()
        val provider = offsetProvider
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            // Not fully visible on the fixed page: it belongs to the moving page.
            val onMovingPage = lm.getDecoratedBottom(child) > bottom
            val tx = if (onMovingPage) shiftX else 0f
            var ty = if (onMovingPage) -shiftY.toFloat() else 0f
            if (provider != null) ty += provider.offsetY(child, onMovingPage, child.top - shiftY, pageTop, pageHeight)
            if (child.translationX != tx) child.translationX = tx
            if (child.translationY != ty) child.translationY = ty
        }
    }

    private fun deactivate() {
        isActive = false
        (rv.layoutManager as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(0)
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            child.translationX = 0f
            child.translationY = 0f
        }
    }

    companion object {
        private val books = WeakHashMap<AllAppsRecyclerView, FoldDrawerBook>()

        /** Called when the drawer container sets up an A-Z RecyclerView. */
        @JvmStatic
        fun attach(rv: AllAppsRecyclerView): FoldDrawerBook = books.getOrPut(rv) { FoldDrawerBook(rv) }

        /** The book of [rv], if it has one. */
        fun of(rv: AllAppsRecyclerView?): FoldDrawerBook? = rv?.let { books[it] }
    }
}
