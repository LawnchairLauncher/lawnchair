package app.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.Launcher
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.views.ActivityContext

class AllAppsHorizontalPagedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : PagedView<PageIndicatorDots>(context, attrs, defStyle) {

    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)
    private var appsList: AlphabeticalAppsList<*>? = null
    private var appsAdapter: BaseAllAppsAdapter<*>? = null
    var isWorkProfile: Boolean = false
        private set
    var onAppsRebuiltListener: Runnable? = null
    private var adapterObserver: RecyclerView.AdapterDataObserver? = null
    private var registeredStore: AllAppsStore<*>? = null

    private var currentRows: Int = 0
    private var currentCols: Int = 0
    private var currentItemCount: Int = 0

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        mPageSpacing = Utilities.dpToPx(16f)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            clearSearchFocusIfNeeded()
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            clearSearchFocusIfNeeded()
        }
        return super.onTouchEvent(ev)
    }

    private fun clearSearchFocusIfNeeded() {
        try {
            val launcher = activityContext as? Launcher
            val searchInput: ExtendedEditText? = launcher?.appsView?.searchUiManager?.editText
            if (searchInput != null && searchInput.hasFocus()) {
                searchInput.clearFocus()
                searchInput.hideKeyboard()
            }
        } catch (_: Exception) {}
    }

    override fun hasOverlappingRendering(): Boolean = false

    fun setAppsList(
        list: AlphabeticalAppsList<*>,
        adapter: BaseAllAppsAdapter<*>?,
        isWork: Boolean,
    ) {
        val oldAdapter = appsAdapter
        adapterObserver?.let { observer ->
            try {
                oldAdapter?.unregisterAdapterDataObserver(observer)
            } catch (_: Exception) {}
        }

        appsList = list
        appsAdapter = adapter
        isWorkProfile = isWork

        val observer = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                rebuildPages()
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                rebuildPages()
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                rebuildPages()
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                rebuildPages()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                rebuildPages()
            }
        }
        adapterObserver = observer
        try {
            adapter?.registerAdapterDataObserver(observer)
        } catch (_: Exception) {}

        rebuildPages()
    }

    fun onAppsUpdated() {
        rebuildPages()
    }

    private fun calculateGridDimensions(): Pair<Int, Int> {
        val dp: DeviceProfile = activityContext.deviceProfile
        val cols = maxOf(1, dp.numShownAllAppsColumns)
        val h = if (measuredHeight > 0) measuredHeight else height
        val availHeight = h - paddingTop - paddingBottom
        val minCellHeight = dp.allAppsProfile.iconSizePx + Utilities.dpToPx(16f)
        val rows = if (availHeight <= 0 || availHeight >= minCellHeight * 6) {
            6
        } else {
            maxOf(1, availHeight / minCellHeight)
        }
        return Pair(cols, rows)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val (cols, rows) = calculateGridDimensions()
        if (rows != currentRows || cols != currentCols) {
            post { rebuildPages() }
        }
    }

    fun registerIconContainers(store: AllAppsStore<*>) {
        registeredStore = store
        for (i in 0 until childCount) {
            val page = getChildAt(i)
            if (page is ViewGroup) {
                store.registerIconContainer(page)
            }
        }
    }

    private fun unregisterIconContainers() {
        val store = registeredStore ?: return
        for (i in 0 until childCount) {
            val page = getChildAt(i)
            if (page is ViewGroup) {
                store.unregisterIconContainer(page)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val adapter = appsAdapter
        adapterObserver?.let {
            try {
                adapter?.unregisterAdapterDataObserver(it)
            } catch (_: Exception) {}
        }
        adapterObserver = null
        unregisterIconContainers()
    }

    fun rebuildPages() {
        val list = appsList ?: return
        val dp: DeviceProfile = activityContext.deviceProfile

        val hasWorkPausedCard = list.adapterItems.any { it.viewType == BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD }
        if (hasWorkPausedCard) {
            unregisterIconContainers()
            removeAllViews()
            val pausedCard = LayoutInflater.from(context).inflate(R.layout.work_apps_paused, this, false)
            val container = FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                val cardParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
                addView(pausedCard, cardParams)
            }
            addView(container)
            mPageIndicator?.visibility = GONE
            currentRows = 0
            currentCols = 0
            currentItemCount = 0
            onAppsRebuiltListener?.run()
            return
        }

        val (cols, rows) = calculateGridDimensions()
        val itemsPerPage = maxOf(1, rows * cols)

        val adapterItems = list.adapterItems.filter { item ->
            BaseAllAppsAdapter.isIconViewType(item.viewType)
        }

        val totalItems = adapterItems.size
        val pageCount = if (totalItems == 0) 1 else ((totalItems + itemsPerPage - 1) / itemsPerPage)

        // Fast-path: If grid dimensions and total item count match, update existing views in-place without re-inflation
        var canReuseViews = rows == currentRows && cols == currentCols && pageCount == childCount && totalItems == currentItemCount
        if (canReuseViews) {
            outer@ for (pageIdx in 0 until pageCount) {
                val pageGrid = getChildAt(pageIdx) as? AllAppsPageGrid ?: run {
                    canReuseViews = false
                    break@outer
                }
                val startIdx = pageIdx * itemsPerPage
                val endIdx = minOf(startIdx + itemsPerPage, totalItems)
                if (pageGrid.childCount != (endIdx - startIdx)) {
                    canReuseViews = false
                    break@outer
                }
                var childIdx = 0
                for (i in startIdx until endIdx) {
                    val item = adapterItems[i]
                    val child = pageGrid.getChildAt(childIdx)
                    val isExpectedFolder = item.viewType == BaseAllAppsAdapter.VIEW_TYPE_FOLDER
                    val isFolder = child is FolderIcon
                    val isApp = child is BubbleTextView
                    if ((isExpectedFolder && !isFolder) || (!isExpectedFolder && !isApp)) {
                        canReuseViews = false
                        break@outer
                    }
                    childIdx++
                }
            }
        }

        if (canReuseViews) {
            outer@ for (pageIdx in 0 until pageCount) {
                val pageGrid = getChildAt(pageIdx) as? AllAppsPageGrid ?: run {
                    canReuseViews = false
                    break@outer
                }
                val startIdx = pageIdx * itemsPerPage
                val endIdx = minOf(startIdx + itemsPerPage, totalItems)
                if (pageGrid.childCount != (endIdx - startIdx)) {
                    canReuseViews = false
                    break@outer
                }
                var childIdx = 0
                for (i in startIdx until endIdx) {
                    val item = adapterItems[i]
                    val child = pageGrid.getChildAt(childIdx)
                    val isExpectedFolder = item.viewType == BaseAllAppsAdapter.VIEW_TYPE_FOLDER
                    val isFolder = child is FolderIcon
                    val isApp = child is BubbleTextView
                    if ((isExpectedFolder && !isFolder) || (!isExpectedFolder && !isApp)) {
                        canReuseViews = false
                        break@outer
                    }
                    childIdx++
                }
            }
        }

        if (canReuseViews) {
            for (pageIdx in 0 until pageCount) {
                val pageGrid = getChildAt(pageIdx) as? AllAppsPageGrid ?: continue
                val startIdx = pageIdx * itemsPerPage
                val endIdx = minOf(startIdx + itemsPerPage, totalItems)
                var childIdx = 0
                for (i in startIdx until endIdx) {
                    val item = adapterItems[i]
                    val child = pageGrid.getChildAt(childIdx)
                    if (child is BubbleTextView && item.itemInfo != null) {
                        child.reset()
                        child.setSkipUserBadge(!list.hasSearchResults() && (list.isWorkAppsList || isWorkProfile))
                        child.applyFromApplicationInfo(item.itemInfo)
                        child.tag = item.itemInfo
                    }
                    childIdx++
                }
            }
            onAppsRebuiltListener?.run()
            return
        }

        currentRows = rows
        currentCols = cols
        currentItemCount = totalItems

        unregisterIconContainers()
        removeAllViews()

        val launcher = activityContext as? Launcher ?: Launcher.getLauncher(context)
        val inflater = LayoutInflater.from(context)
        val iconLayout = if (dp.inv.enableTwoLinesInAllApps) {
            R.layout.all_apps_icon_twoline
        } else {
            R.layout.all_apps_icon
        }

        val clickListener = activityContext.itemOnClickListener
        val longClickListener = activityContext.allAppsItemLongClickListener
        val focusListener = launcher.focusHandler

        for (pageIdx in 0 until pageCount) {
            val pageGrid = AllAppsPageGrid(context, cols, rows)
            val startIdx = pageIdx * itemsPerPage
            val endIdx = minOf(startIdx + itemsPerPage, totalItems)

            for (i in startIdx until endIdx) {
                val item = adapterItems[i]
                if (item.viewType == BaseAllAppsAdapter.VIEW_TYPE_FOLDER && item.folderInfo != null) {
                    val folderIcon = FolderIcon.inflateFolderAndIcon(
                        R.layout.all_apps_folder_icon,
                        launcher,
                        pageGrid,
                        item.folderInfo,
                    )
                    folderIcon.setOnLongClickListener(longClickListener)
                    pageGrid.addView(folderIcon)
                } else if (item.itemInfo != null) {
                    val icon = inflater.inflate(iconLayout, pageGrid, false) as BubbleTextView
                    icon.setLongPressTimeoutFactor(1f)
                    icon.onFocusChangeListener = focusListener
                    icon.setOnClickListener(clickListener)
                    icon.setOnLongClickListener(longClickListener)
                    icon.reset()
                    icon.setSkipUserBadge(!list.hasSearchResults() && (list.isWorkAppsList || isWorkProfile))
                    icon.applyFromApplicationInfo(item.itemInfo)
                    pageGrid.addView(icon)
                }
            }

            addView(pageGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        mPageIndicator?.let { indicator ->
            indicator.setMarkersCount(pageCount)
            indicator.setActiveMarker(nextPage.coerceIn(0, maxOf(0, pageCount - 1)))
            indicator.visibility = if (pageCount > 1) VISIBLE else GONE
            indicator.setShouldAutoHide(false)
        }

        registeredStore?.let { store ->
            registerIconContainers(store)
        }

        if (currentPage >= pageCount) {
            setCurrentPage(maxOf(0, pageCount - 1))
        }
        requestLayout()
        onAppsRebuiltListener?.run()
    }
}

class AllAppsPageGrid(
    context: Context,
    private val cols: Int,
    private val rows: Int,
) : ViewGroup(context) {

    init {
        clipToPadding = false
        clipChildren = true
    }

    override fun hasOverlappingRendering(): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val availWidth = maxOf(0, width - paddingLeft - paddingRight)
        val availHeight = maxOf(0, height - paddingTop - paddingBottom)

        val cellWidth = if (cols > 0) availWidth / cols else availWidth
        val cellHeight = if (rows > 0) availHeight / rows else availHeight

        val childWidthSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(childWidthSpec, childHeightSpec)
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availWidth = maxOf(0, (r - l) - paddingLeft - paddingRight)
        val availHeight = maxOf(0, (b - t) - paddingTop - paddingBottom)

        val cellWidth = if (cols > 0) availWidth / cols else availWidth
        val cellHeight = if (rows > 0) availHeight / rows else availHeight

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val col = i % cols
            val row = i / cols

            val childLeft = paddingLeft + col * cellWidth
            val childTop = paddingTop + row * cellHeight
            val childRight = childLeft + cellWidth
            val childBottom = childTop + cellHeight

            child.layout(childLeft, childTop, childRight, childBottom)
        }
    }
}
