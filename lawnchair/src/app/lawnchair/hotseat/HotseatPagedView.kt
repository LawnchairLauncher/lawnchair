package app.lawnchair.hotseat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.pageindicators.PageIndicatorDots

/**
 * Horizontally pageable container for dock [CellLayout] pages.
 */
class HotseatPagedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : PagedView<PageIndicatorDots>(context, attrs, defStyle) {

    fun interface OnDockPageChangeListener {
        fun onDockPageChanged(page: Int)
    }

    var isPagingEnabled: Boolean = false
        private set(value) {
            field = value
            applyPageIndicatorVisibility()
        }

    private var onDockPageChangeListener: OnDockPageChangeListener? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPageIndicator(indicator: PageIndicatorDots?) {
        mPageIndicator = indicator
        mPageIndicator?.let {
            it.setMarkersCount(childCount)
            it.setActiveMarker(nextPage)
        }
        applyPageIndicatorVisibility()
    }

    private fun applyPageIndicatorVisibility() {
        val indicator = mPageIndicator ?: return
        if (isPagingEnabled) {
            indicator.visibility = VISIBLE
            // Same as workspace: show while paging, fade out when idle.
            indicator.setShouldAutoHide(true)
        } else {
            indicator.setShouldAutoHide(false)
            indicator.visibility = GONE
        }
    }

    override fun getPageAt(index: Int): CellLayout? = getChildAt(index) as? CellLayout

    fun getCurrentCellLayout(): CellLayout? = getPageAt(nextPage)

    fun resetPages(hasVerticalHotseat: Boolean, workspace: Workspace<*>?, dp: DeviceProfile) {
        removeAllViews()
        val pageCount = if (hasVerticalHotseat) 1 else maxOf(1, dp.numHotseatPages)
        isPagingEnabled = pageCount > 1

        val inflater = LayoutInflater.from(context)
        for (i in 0 until pageCount) {
            val page = inflater.inflate(R.layout.hotseat_page, this, false) as CellLayout
            page.setHotseatPageIndex(i)
            if (workspace != null) {
                page.setCellLayoutContainer(workspace)
            }
            page.resetCellSize(dp)
            page.isLongClickable = false
            page.isHapticFeedbackEnabled = false
            if (hasVerticalHotseat) {
                page.setGridSize(1, dp.numShownHotseatIcons)
            } else {
                page.setGridSize(dp.numShownHotseatIcons, dp.numHotseatRows)
            }
            addView(page, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        mPageIndicator?.let {
            it.setMarkersCount(pageCount)
            it.setActiveMarker(0)
        }
        applyPageIndicatorVisibility()
        setCurrentPage(0)
        requestLayout()
    }

    fun setOnDockPageChangeListener(listener: OnDockPageChangeListener?) {
        onDockPageChangeListener = listener
    }

    override fun onPageEndTransition() {
        super.onPageEndTransition()
        onDockPageChangeListener?.onDockPageChanged(nextPage)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (mMaxScroll > 0) {
            mPageIndicator?.setScroll(l, mMaxScroll)
        }
    }

    // LC: icon swipe gestures. A touch that starts on a dock icon with a horizontal swipe
    // gesture belongs to the icon, not to the dock pager.
    private var disallowInterceptForIconSwipe = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isPagingEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                disallowInterceptForIconSwipe = isTouchOnIconWithHorizontalSwipeGesture(ev.x, ev.y)
                if (disallowInterceptForIconSwipe) {
                    resetTouchState()
                    return false
                }
            }

            MotionEvent.ACTION_MOVE -> if (disallowInterceptForIconSwipe) return false

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (disallowInterceptForIconSwipe) {
                disallowInterceptForIconSwipe = false
                resetTouchState()
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun isTouchOnIconWithHorizontalSwipeGesture(x: Float, y: Float): Boolean {
        // Children are laid out in scrolled content coordinates.
        val contentX = x + scrollX
        val contentY = y + scrollY
        for (i in childCount - 1 downTo 0) {
            val page = getChildAt(i) as? CellLayout ?: continue
            val pageX = contentX - page.left
            val pageY = contentY - page.top
            if (!Utilities.pointInView(page, pageX, pageY, 0f)) continue
            val container = page.shortcutsAndWidgets
            val containerX = pageX - container.left
            val containerY = pageY - container.top
            for (j in container.childCount - 1 downTo 0) {
                val child = container.getChildAt(j)
                if (child !is BubbleTextView || child.visibility != VISIBLE) continue
                if (Utilities.pointInView(child, containerX - child.left, containerY - child.top, 0f)) {
                    return child.hasConfiguredHorizontalIconSwipeGesture()
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isPagingEnabled) return false
        return super.onTouchEvent(ev)
    }

    /** Returns the page CellLayout under the given x in this view's coordinates. */
    fun findPageAtLocalX(x: Float): CellLayout? {
        val width = measuredWidth
        if (width <= 0 || childCount == 0) {
            return getCurrentCellLayout()
        }
        var raw = (scrollX + x).toInt() / width
        if (mIsRtl) {
            raw = childCount - 1 - raw
        }
        val page = raw.coerceIn(0, childCount - 1)
        return getPageAt(page)
    }
}
