package app.lawnchair.allapps

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
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
    private var currentPrivateRows: Int = 0

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
        val lineCount = if (dp.inv.enableTwoLinesInAllApps) 2 else 1
        val textHeight = Utilities.calculateTextHeight(dp.allAppsProfile.iconTextSizePx) * lineCount
        val minCellHeight = dp.allAppsProfile.iconSizePx + dp.allAppsProfile.iconDrawablePaddingPx + textHeight
        val rows = if (availHeight <= 0 || availHeight >= minCellHeight * 6) {
            6
        } else {
            maxOf(1, availHeight / minCellHeight)
        }
        return Pair(cols, rows)
    }

    private fun calculatePrivateRows(rows: Int): Int {
        val dp: DeviceProfile = activityContext.deviceProfile
        val psHeaderHeight = context.resources.getDimensionPixelSize(R.dimen.ps_header_height)
        val h = if (measuredHeight > 0) measuredHeight else height
        val availHeight = h - paddingTop - paddingBottom
        if (availHeight <= 0) return maxOf(1, rows - 1)
        val lineCount = if (dp.inv.enableTwoLinesInAllApps) 2 else 1
        val textHeight = Utilities.calculateTextHeight(dp.allAppsProfile.iconTextSizePx) * lineCount
        val minCellHeight = dp.allAppsProfile.iconSizePx + dp.allAppsProfile.iconDrawablePaddingPx + textHeight
        val privateAvailHeight = maxOf(0, availHeight - psHeaderHeight)
        return if (privateAvailHeight >= minCellHeight * rows) {
            rows
        } else {
            maxOf(1, privateAvailHeight / minCellHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (oldw == 0 && oldh == 0) {
            post { rebuildPages() }
            return
        }
        val (cols, rows) = calculateGridDimensions()
        val privateRows = calculatePrivateRows(rows)
        if (rows != currentRows || cols != currentCols || privateRows != currentPrivateRows) {
            post { rebuildPages() }
        }
    }

    fun registerIconContainers(store: AllAppsStore<*>) {
        registeredStore = store
        for (i in 0 until childCount) {
            val page = getChildAt(i)
            if (page is ViewGroup) {
                findAndRegisterGrids(page, store)
            }
        }
    }

    private fun findAndRegisterGrids(view: ViewGroup, store: AllAppsStore<*>) {
        if (view is AllAppsPageGrid) {
            store.registerIconContainer(view)
        } else {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is ViewGroup) {
                    findAndRegisterGrids(child, store)
                }
            }
        }
    }

    private fun unregisterIconContainers() {
        val store = registeredStore ?: return
        for (i in 0 until childCount) {
            val page = getChildAt(i)
            if (page is ViewGroup) {
                findAndUnregisterGrids(page, store)
            }
        }
    }

    private fun findAndUnregisterGrids(view: ViewGroup, store: AllAppsStore<*>) {
        if (view is AllAppsPageGrid) {
            store.unregisterIconContainer(view)
        } else {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is ViewGroup) {
                    findAndUnregisterGrids(child, store)
                }
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

        val psHeaderIndex = if (!isWorkProfile) {
            list.adapterItems.indexOfFirst { it.viewType == BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER }
        } else {
            -1
        }
        val hasPrivateSpace = psHeaderIndex != -1

        val personalItems = if (hasPrivateSpace) {
            list.adapterItems.subList(0, psHeaderIndex).filter {
                BaseAllAppsAdapter.isIconViewType(it.viewType)
            }
        } else {
            list.adapterItems.filter { BaseAllAppsAdapter.isIconViewType(it.viewType) }
        }

        val privateItems = if (hasPrivateSpace) {
            list.adapterItems.subList(psHeaderIndex + 1, list.adapterItems.size).filter {
                BaseAllAppsAdapter.isIconViewType(it.viewType)
            }
        } else {
            emptyList()
        }

        val personalPageCount = if (personalItems.isEmpty()) 1 else ((personalItems.size + itemsPerPage - 1) / itemsPerPage)

        val privateRows = calculatePrivateRows(rows)
        val privateItemsPerPage = maxOf(1, privateRows * cols)

        val privatePageCount = if (hasPrivateSpace) {
            if (privateItems.isEmpty()) 1 else maxOf(1, (privateItems.size + privateItemsPerPage - 1) / privateItemsPerPage)
        } else {
            0
        }

        val pageCount = personalPageCount + privatePageCount
        val totalItems = personalItems.size + privateItems.size

        // Fast-path: When there is no private space, reuse existing views if grid dims and item count match
        var canReuseViews = !hasPrivateSpace && rows == currentRows && cols == currentCols && pageCount == childCount && totalItems == currentItemCount
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
                    val item = personalItems[i]
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
                    val item = personalItems[i]
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
        currentPrivateRows = privateRows

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

        // 1. Personal Pages
        for (pageIdx in 0 until personalPageCount) {
            val pageGrid = AllAppsPageGrid(context, cols, rows)
            val startIdx = pageIdx * itemsPerPage
            val endIdx = minOf(startIdx + itemsPerPage, personalItems.size)

            populateGridWithItems(
                pageGrid, personalItems, startIdx, endIdx,
                launcher, inflater, iconLayout, clickListener, longClickListener, focusListener,
                isWork = isWorkProfile, hasSearchResults = list.hasSearchResults(),
            )
            addView(pageGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        // 2. Private Space Page(s)
        if (hasPrivateSpace) {
            if (privateItems.isEmpty()) {
                // Locked state: Dedicated Private Space Locked Page
                val lockedPage = createPrivateSpaceLockedPage()
                addView(lockedPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            } else {
                // Unlocked state: Dedicated Private Space Page(s) with Header and Grid
                for (privPageIdx in 0 until privatePageCount) {
                    val pageContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        clipToPadding = false
                        clipChildren = true
                    }
                    val psHeader = inflater.inflate(R.layout.private_space_header, pageContainer, false) as? RelativeLayout
                    if (psHeader != null) {
                        launcher.appsView?.privateProfileManager?.bindPrivateSpaceHeaderViewElements(psHeader)
                        pageContainer.addView(
                            psHeader,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                    val pageGrid = AllAppsPageGrid(context, cols, privateRows)
                    val startIdx = privPageIdx * privateItemsPerPage
                    val endIdx = minOf(startIdx + privateItemsPerPage, privateItems.size)

                    populateGridWithItems(
                        pageGrid, privateItems, startIdx, endIdx,
                        launcher, inflater, iconLayout, clickListener, longClickListener, focusListener,
                        isWork = false, hasSearchResults = list.hasSearchResults(),
                    )
                    pageContainer.addView(
                        pageGrid,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            0,
                            1f,
                        ),
                    )
                    addView(pageContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            }
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

    private fun populateGridWithItems(
        pageGrid: AllAppsPageGrid,
        items: List<BaseAllAppsAdapter.AdapterItem>,
        startIdx: Int,
        endIdx: Int,
        launcher: Launcher,
        inflater: LayoutInflater,
        iconLayout: Int,
        clickListener: View.OnClickListener,
        longClickListener: View.OnLongClickListener,
        focusListener: View.OnFocusChangeListener,
        isWork: Boolean,
        hasSearchResults: Boolean,
    ) {
        for (i in startIdx until endIdx) {
            val item = items[i]
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
                icon.setSkipUserBadge(!hasSearchResults && (isWork || isWorkProfile))
                icon.applyFromApplicationInfo(item.itemInfo)
                pageGrid.addView(icon)
            }
        }
    }

    private fun createPrivateSpaceLockedPage(): View {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = Utilities.dpToPx(24f)
            setPadding(pad, pad, pad, pad)
        }

        val iconView = ImageView(context).apply {
            setImageResource(R.drawable.ic_pr_lock)
            val padIcon = Utilities.dpToPx(14f)
            setPadding(padIcon, padIcon, padIcon, padIcon)
            val shape = GradientDrawable().apply {
                this.shape = GradientDrawable.OVAL
                setColor(context.getColor(R.color.materialColorPrimaryContainer))
            }
            background = shape
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.materialColorOnPrimaryContainer))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconSize = Utilities.dpToPx(56f)
        val iconLp = LinearLayout.LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Utilities.dpToPx(16f)
        }
        content.addView(iconView, iconLp)

        val titleView = TextView(context).apply {
            text = context.getString(R.string.ps_container_title)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(context.getColor(R.color.materialColorOnSurface))
            gravity = Gravity.CENTER
        }
        val titleLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Utilities.dpToPx(8f)
        }
        content.addView(titleView, titleLp)

        val subtitleView = TextView(context).apply {
            text = context.getString(R.string.ps_container_lock_button_content_description)
            textSize = 14f
            setTextColor(context.getColor(R.color.materialColorOnSurfaceVariant))
            gravity = Gravity.CENTER
        }
        val subLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Utilities.dpToPx(24f)
        }
        content.addView(subtitleView, subLp)

        val unlockButton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val hPad = Utilities.dpToPx(24f)
            val vPad = Utilities.dpToPx(12f)
            setPadding(hPad, vPad, hPad, vPad)
            val bg = GradientDrawable().apply {
                this.shape = GradientDrawable.RECTANGLE
                cornerRadius = Utilities.dpToPx(24f).toFloat()
                setColor(context.getColor(R.color.materialColorPrimary))
            }
            background = bg
            isClickable = true
            isFocusable = true

            val btnIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_pr_lock)
                imageTintList = ColorStateList.valueOf(context.getColor(R.color.materialColorOnPrimary))
                val size = Utilities.dpToPx(18f)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = Utilities.dpToPx(8f)
                }
            }
            addView(btnIcon)

            val btnText = TextView(context).apply {
                text = "Unlock"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(context.getColor(R.color.materialColorOnPrimary))
            }
            addView(btnText)

            setOnClickListener {
                val launcher = activityContext as? Launcher ?: Launcher.getLauncher(context)
                launcher.appsView?.privateProfileManager?.setQuietMode(false)
            }
        }
        val btnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(unlockButton, btnLp)

        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return container
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
