package app.lawnchair.fold

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
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
 * Keyboard and accessibility follow the visual order, fixed page rows then second page rows: in
 * layout order the second page's rows already come right after the fixed page's, so the default
 * focus search (up, down, tab) and the accessibility traversal (children sorted by layout position)
 * read the pages in order. As a [ScrollableLayoutManager.FoldBookDelegate] the book also moves
 * left/right focus across the hinge by where the items are drawn, keeps the list still when a
 * focused item is already visible on the second page, and scrolls only as much as needed when it
 * is cut there. The focus outline follows the item ([offsetFocusRect]).
 *
 * One instance per A-Z RecyclerView (personal and work); it does nothing unless the current
 * profile was built for PAGES.
 */
class FoldDrawerBook private constructor(
    private val rv: AllAppsRecyclerView,
) : ViewTreeObserver.OnPreDrawListener,
    View.OnAttachStateChangeListener,
    ScrollableLayoutManager.FoldBookDelegate {

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

    /** How far the second page's children are moved up, in pixels, while active. */
    private var shiftY = 0

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
        (lm as? ScrollableLayoutManager)?.let {
            it.setFoldExtraLayoutSpace(pageHeight)
            it.setFoldBookDelegate(this)
        }
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
        shiftY = if (rowHeight > 0) (pageHeight / rowHeight).coerceAtLeast(1) * rowHeight else pageHeight
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
        (rv.layoutManager as? ScrollableLayoutManager)?.let {
            it.setFoldExtraLayoutSpace(0)
            it.setFoldBookDelegate(null)
        }
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            child.translationX = 0f
            child.translationY = 0f
        }
    }

    /** Whether [child] is on the second page (not fully visible on the fixed page). */
    private fun isOnMovingPage(child: View): Boolean {
        val lm = rv.layoutManager ?: return false
        return lm.getDecoratedBottom(child) > rv.height - rv.paddingBottom
    }

    /** Whether the drawn [child] shows on one of the pages, at least partly. */
    private fun isDrawnOnPage(child: View): Boolean {
        val top = child.top + child.translationY
        return top + child.height > pageTop && top < pageTop + pageHeight
    }

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        if (!isActive || (direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT)) return null
        val item = rv.findContainingItemView(focused) ?: return null
        val left = item.left + item.translationX
        val right = left + item.width
        val top = item.top + item.translationY
        val bottom = top + item.height
        val centerX = (left + right) / 2f
        // The nearest item drawn on the same row in that direction, across the hinge if needed
        var best: View? = null
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            if (child === item || !child.isFocusable || child.visibility != View.VISIBLE || !isDrawnOnPage(child)) continue
            val childTop = child.top + child.translationY
            if (childTop >= bottom || childTop + child.height <= top) continue
            val childCenterX = child.left + child.translationX + child.width / 2f
            val distance = if (direction == View.FOCUS_LEFT) centerX - childCenterX else childCenterX - centerX
            if (distance > 0f && distance < bestDistance) {
                bestDistance = distance
                best = child
            }
        }
        return best
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
    ): Boolean? {
        if (!isActive || !isOnMovingPage(child)) return null
        // On the second page: no scroll while it shows there whole, else just enough to show it.
        val top = child.top - shiftY
        val bottom = top + child.height
        val pageBottom = pageTop + pageHeight
        val dy = when {
            bottom > pageBottom -> bottom - pageBottom
            top < pageTop -> top - pageTop
            else -> 0
        }
        if (dy == 0) return false
        if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy)
        return true
    }

    companion object {
        private val books = WeakHashMap<AllAppsRecyclerView, FoldDrawerBook>()

        /** Moves a focus outline [rect] (in the list's coordinates) to where [view] is drawn. */
        @JvmStatic
        fun offsetFocusRect(view: View, rect: Rect) {
            val list = view.parent as? AllAppsRecyclerView ?: return
            if (books[list]?.isActive != true) return
            rect.offset(view.translationX.toInt(), view.translationY.toInt())
        }

        /** Called when the drawer container sets up an A-Z RecyclerView. */
        @JvmStatic
        fun attach(rv: AllAppsRecyclerView): FoldDrawerBook = books.getOrPut(rv) { FoldDrawerBook(rv) }

        /** The book of [rv], if it has one. */
        fun of(rv: AllAppsRecyclerView?): FoldDrawerBook? = rv?.let { books[it] }
    }
}
