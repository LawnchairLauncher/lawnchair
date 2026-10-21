package app.lawnchair.fold

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.util.ScrollableLayoutManager
import com.android.launcher3.views.ActivityContext
import kotlin.math.min

/**
 * The second page of the "book" drawer ([FoldDrawerLayout.PAGES]).
 *
 * The A-Z grid is laid out on the first page's half only
 * ([FoldDrawerLayouts.adjustAppsGridPadding]): the fixed half, or with the "Page order" option
 * [FoldPageOrder.READING] the language's start side ([FoldDrawerLayouts.Drawer]). One more
 * screenful of rows is laid out below the visible ones. Before every frame, every child that is
 * not fully visible on the first page (its bottom is past the first page's bottom edge) is moved
 * onto the other half, up by as many whole rows as fit on a page. So the first page only ever
 * shows full rows, the row it cuts is the first row of the second page, no app is hidden, and
 * the rows of both pages line up. Both pages scroll together, by rows: when a row becomes fully
 * visible at the bottom of the first page it leaves the top of the second page; until then the
 * part of it above the grid's top edge is clipped, since the second page has no header rows to
 * slide under. The RecyclerView,
 * its fast scroller, focus and accessibility stay as they are; only view translations change, and
 * touch, drag and accessibility bounds follow them.
 *
 * The list ends when its last row reaches the bottom of the second page, not of the first one,
 * which would leave the second page empty: the [ScrollableLayoutManager] stops scrolling there
 * ([scrollToEnd]), for drags, flings and the fast scroller alike, and its scroll range is shorter by
 * the second page's shift ([scrollRangeCut]) so the scroll bar ends there too.
 *
 * A row moves to the first page only once it is whole there, so while the list scrolls the row
 * at the first page's bottom shows on the second page. When a drag or fling stops, the list settles
 * on the nearest row ([settle]): a row starts at the top of both pages, the first page shows whole
 * rows and the second page starts with the next one, so no row is left cut between them.
 *
 * Keyboard and accessibility follow the visual order, first page rows then second page rows: in
 * layout order the second page's rows already come right after the first page's, so the default
 * focus search (up, down, tab) and the accessibility traversal (children sorted by layout position)
 * read the pages in order. Called by the list's [ScrollableLayoutManager], the book also moves
 * left/right focus across the hinge by where the items are drawn, keeps the list still when a
 * focused item is already visible on the second page, and scrolls only as much as needed when it
 * is cut there. The focus outline follows the item ([offsetFocusRect]).
 *
 * While a drawer transition plays on the book ([transitionProgress]), its second page's apps rise
 * from one page below into place, top row first.
 *
 * One instance per A-Z RecyclerView (personal and work) of a foldable, kept on the list itself (a
 * view tag), so it lives exactly as long as the list. It listens to frames only while the current
 * profile is built for PAGES ([onDeviceProfileChanged]); while a drawer transition plays, the
 * transition drives [update] instead.
 */
class FoldDrawerBook private constructor(
    private val rv: AllAppsRecyclerView,
) : ViewTreeObserver.OnPreDrawListener,
    View.OnAttachStateChangeListener {

    /**
     * Progress of the drawer transition playing on the book (0 = the second page still one page
     * below), or NaN when none plays. While it is set, the transition calls [update] every frame.
     */
    var transitionProgress = Float.NaN

    /** Whether the book is laid out (the current profile was built for PAGES). */
    var isActive = false
        private set

    /** Top of both pages' grids and their height, in RecyclerView coordinates, while active. */
    private var pageTop = 0
    private var pageHeight = 0

    private var observer: ViewTreeObserver? = null

    /** How far the second page's children are moved up, in pixels, while active. */
    private var shiftY = 0

    /** Children of the second page clipped at its top edge. */
    private val clipped = ArrayList<View>()
    private val clipRect = Rect()

    /** Whether the list was dragged since it was last idle: only then does it [settle]. */
    private var dragged = false

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                dragged = true
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE && dragged) {
                dragged = false
                settle()
            }
        }
    }

    init {
        rv.addOnAttachStateChangeListener(this)
        rv.addOnScrollListener(scrollListener)
        if (rv.isAttachedToWindow) onViewAttachedToWindow(rv)
    }

    override fun onViewAttachedToWindow(v: View) = onDeviceProfileChanged()

    override fun onViewDetachedFromWindow(v: View) = listen(false)

    override fun onPreDraw(): Boolean {
        if (transitionProgress.isNaN()) update()
        return true
    }

    /**
     * Listens to frames only while the profile is built for PAGES, and puts the children back when
     * it no longer is. Called when the list is attached and after each profile change.
     */
    fun onDeviceProfileChanged() {
        val pages = isPages(deviceProfile())
        listen(pages && rv.isAttachedToWindow)
        if (!pages && isActive) deactivate()
    }

    private fun listen(on: Boolean) {
        if (on == (observer != null)) return
        if (on) {
            observer = rv.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        } else {
            observer?.let { if (it.isAlive) it.removeOnPreDrawListener(this) }
            observer = null
        }
    }

    private fun deviceProfile(): DeviceProfile {
        val activity: ActivityContext = ActivityContext.lookupContext(rv.context)
        return activity.deviceProfile
    }

    private fun isPages(dp: DeviceProfile) = FoldDrawerLayouts.drawerLayoutOf(dp) == FoldDrawerLayout.PAGES

    /** Places the children on their page. Safe to call any time. */
    fun update() {
        val dp = deviceProfile()
        if (!isPages(dp) || rv.height == 0) {
            if (isActive) deactivate()
            return
        }
        isActive = true
        val lm = rv.layoutManager ?: return
        val bottom = rv.height - rv.paddingBottom
        pageTop = rv.paddingTop
        pageHeight = (bottom - pageTop).coerceAtLeast(0)
        (lm as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(pageHeight)
        // Up by whole rows, so the cut row lands where the first page's first full row would be
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
        val rising = !transitionProgress.isNaN() && pageHeight > 0
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            // Not fully visible on the first page: it belongs to the second page.
            val onSecondPage = lm.getDecoratedBottom(child) > bottom
            val tx = if (onSecondPage) shiftX else 0f
            var ty = if (onSecondPage) -shiftY.toFloat() else 0f
            if (onSecondPage && rising) ty += riseOffset(child.top - shiftY)
            if (child.translationX != tx) child.translationX = tx
            if (child.translationY != ty) child.translationY = ty
            clipAbovePage(child, if (onSecondPage) pageTop - (child.top + ty) else 0f)
        }
        // Views that left the list (recycled) lose their clip
        for (i in clipped.size - 1 downTo 0) {
            val view = clipped[i]
            if (view.parent !== rv) {
                view.clipBounds = null
                clipped.removeAt(i)
            }
        }
    }

    /** How far below its place a second-page child whose top is at [movingTop] still is. */
    private fun riseOffset(movingTop: Int): Float {
        val depth = ((movingTop - pageTop).toFloat() / pageHeight).coerceIn(0f, 1f)
        val local = ((transitionProgress - depth * FoldTunables.DRAWER_MAX_DELAY) / (1f - FoldTunables.DRAWER_MAX_DELAY)).coerceIn(0f, 1f)
        return (1f - FoldTunables.DRAWER_EASE.getInterpolation(local)) * pageHeight
    }

    /** Clips the top [hidden] pixels of [child] (none when [hidden] <= 0). */
    private fun clipAbovePage(child: View, hidden: Float) {
        if (hidden > 0f) {
            clipRect.set(0, hidden.toInt().coerceAtMost(child.height), child.width, child.height)
            child.clipBounds = clipRect
            if (!clipped.contains(child)) clipped.add(child)
        } else if (clipped.remove(child)) {
            child.clipBounds = null
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
        for (i in clipped.indices) clipped[i].clipBounds = null
        clipped.clear()
    }

    /**
     * How far the list can still scroll down before its last row reaches the bottom of the second
     * page; negative when a layout put it past that (a fold, a scroll to a position, apps removed).
     * [Int.MAX_VALUE] when the book isn't active or the last item isn't laid out (it is then at
     * least a row away, as a page of rows is laid out below the visible ones).
     */
    fun scrollToEnd(): Int {
        if (!isActive) return Int.MAX_VALUE
        val lm = rv.layoutManager ?: return Int.MAX_VALUE
        val count = lm.itemCount
        val last = (if (count > 0) lm.findViewByPosition(count - 1) else null) ?: return Int.MAX_VALUE
        return FoldBookScroll.toEnd(lm.getDecoratedBottom(last), shiftY, pageTop + pageHeight)
    }

    /** How much shorter the list's scroll range is than its items' height, see [scrollToEnd]. */
    val scrollRangeCut: Int get() = if (isActive) shiftY else 0

    /**
     * After a drag or a fling, scrolls by less than a row so that a row starts at the top of the
     * pages ([FoldBookScroll.settle]), or back to the end when a layout left the list past it. Not
     * at the top or the end, nor while the fast scroller is dragged or a transition plays; keyboard,
     * accessibility and fast scroller scrolls don't drag the list, so they never settle.
     */
    private fun settle() {
        if (!isActive || !transitionProgress.isNaN() || rv.scrollbar?.isDraggingThumb == true) return
        if (rv.computeVerticalScrollOffset() <= 0) return
        val lm = rv.layoutManager ?: return
        // The top row reaching into the pages
        var rowTop = Int.MAX_VALUE
        var rowBottom = 0
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val top = lm.getDecoratedTop(child)
            val bottom = lm.getDecoratedBottom(child)
            if (bottom > pageTop && top < rowTop) {
                rowTop = top
                rowBottom = bottom
            }
        }
        if (rowTop == Int.MAX_VALUE) return
        val dy = FoldBookScroll.settle(rowTop - pageTop, rowBottom - pageTop, scrollToEnd())
        if (dy != 0) rv.smoothScrollBy(0, dy)
    }

    /** Whether [child] is on the second page (not fully visible on the first page). */
    private fun isOnSecondPage(child: View): Boolean {
        val lm = rv.layoutManager ?: return false
        return lm.getDecoratedBottom(child) > rv.height - rv.paddingBottom
    }

    /** Whether the drawn [child] shows on one of the pages, at least partly. */
    private fun isDrawnOnPage(child: View): Boolean {
        val top = child.top + child.translationY
        return top + child.height > pageTop && top < pageTop + pageHeight
    }

    /** A child to focus instead of the default search result, or null for the default. */
    fun onInterceptFocusSearch(focused: View, direction: Int): View? {
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

    /** Brings [child] on screen. Returns null to use the default behaviour, else whether the list scrolled. */
    fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, immediate: Boolean): Boolean? {
        if (!isActive || !isOnSecondPage(child)) return null
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
        /** Moves a focus outline [rect] (in the list's coordinates) to where [view] is drawn. */
        @JvmStatic
        fun offsetFocusRect(view: View, rect: Rect) {
            val list = view.parent as? AllAppsRecyclerView ?: return
            if (of(list)?.isActive != true) return
            rect.offset(view.translationX.toInt(), view.translationY.toInt())
        }

        /** Called when the drawer container sets up an A-Z RecyclerView. Only foldables get a book. */
        @JvmStatic
        fun attach(rv: AllAppsRecyclerView) {
            if (InvariantDeviceProfile.deviceType != InvariantDeviceProfile.TYPE_MULTI_DISPLAY) return
            if (of(rv) == null) rv.setTag(R.id.fold_drawer_book, FoldDrawerBook(rv))
        }

        /** The book of [rv], if it has one. */
        @JvmStatic
        fun of(rv: RecyclerView?): FoldDrawerBook? = rv?.getTag(R.id.fold_drawer_book) as? FoldDrawerBook
    }
}

/** The scroll arithmetic of the book, in pixels of the list's layout coordinates. Pure. */
internal object FoldBookScroll {

    /**
     * How far the list can scroll down until its last row, whose bottom is laid out at
     * [lastBottom], is drawn at [pageBottom] on the second page, moved up by [shiftY] (negative:
     * past that).
     */
    fun toEnd(lastBottom: Int, shiftY: Int, pageBottom: Int): Int = lastBottom - shiftY - pageBottom

    /**
     * How far to scroll once the list stops, the row at the pages' top being from [rowTop] to
     * [rowBottom] (relative to that top) and [toEnd] left to the end ([FoldDrawerBook.scrollToEnd]):
     * the row whole into the pages or out of them, whichever is nearer (into them on a tie), but
     * not past the end. Nothing at the end, and back to it when past it.
     */
    fun settle(rowTop: Int, rowBottom: Int, toEnd: Int): Int {
        if (toEnd <= 0) return toEnd
        if (rowTop >= 0) return 0
        val down = min(rowBottom, toEnd)
        return if (-rowTop <= down) rowTop else down
    }
}
