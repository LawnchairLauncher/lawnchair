package app.lawnchair.allapps.suggestions

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.FloatingHeaderRow
import com.android.launcher3.allapps.FloatingHeaderView
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Frequent, Recent and New/Updated apps at the top of the app drawer: the drawer's own
 * Personal/Work tab strip over up to two rows of drawer icons, in the A-Z grid's columns. It takes
 * the suggested apps row's place from a tap on the search box until the user leaves search; a
 * typed query covers it with the search results as usual.
 *
 * The header is sized for the fullest tab, so switching tabs never moves the list below. Children:
 * the tab strip, the empty-tab message, then the icons.
 */
class AppSuggestionsHeaderView(context: Context, attrs: AttributeSet?) :
    ViewGroup(context, attrs),
    FloatingHeaderRow,
    View.OnFocusChangeListener,
    AllAppsStore.OnUpdateListener,
    DeviceProfile.OnDeviceProfileChangeListener,
    StateManager.StateListener<LauncherState> {

    /** Null in the taskbar drawer, which keeps the tabs off. */
    private val launcher = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .firstNotNullOfOrNull { it as? LawnchairLauncher }
    private val prefs = PreferenceManager2.getInstance(context)
    private val tabsTop = resources.getDimensionPixelSize(R.dimen.all_apps_tabs_margin_top)
    private val tabsHeight = resources.getDimensionPixelSize(R.dimen.all_apps_header_pill_height)
    private lateinit var tabs: PersonalWorkSlidingTabStrip
    private lateinit var emptyText: TextView

    /** The apps of each tab, in tab order. */
    private var suggestions: List<List<AppInfo>> = emptyList()
    private var loadJob: Job? = null

    /** From the search box gaining focus until the user leaves search. */
    private var searchActive = false
    private var selectedTab = 0
    private var tabPicked = false
    private var columns = 1
    private var rows = 0
    private var reportedHeight = 0
    private var iconsProfile: DeviceProfile? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        tabs = getChildAt(0) as PersonalWorkSlidingTabStrip
        emptyText = getChildAt(1) as TextView
        for (i in 0 until tabs.childCount) {
            tabs.getChildAt(i).setOnClickListener {
                selectedTab = i
                tabPicked = true
                update()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val appsView = launcher?.appsView ?: return
        launcher.addOnDeviceProfileChangeListener(this)
        launcher.stateManager.addStateListener(this)
        appsView.appsStore.addUpdateListener(this)
        // Lets the store update notification dots and install progress on these icons.
        appsView.appsStore.registerIconContainer(this)
        appsView.searchUiManager.editText?.addOnFocusChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val appsView = launcher?.appsView ?: return
        launcher.removeOnDeviceProfileChangeListener(this)
        launcher.stateManager.removeStateListener(this)
        appsView.appsStore.removeUpdateListener(this)
        appsView.appsStore.unregisterIconContainer(this)
        appsView.searchUiManager.editText?.removeOnFocusChangeListener(this)
        loadJob?.cancel()
    }

    /** The drawer's search box gained or lost focus. */
    override fun onFocusChange(v: View, hasFocus: Boolean) {
        // Dragging the list takes the focus without leaving search. Keep the tabs then: a new
        // header height would stop the drag and scroll the list back to the top.
        if (!hasFocus && launcher?.appsView?.activeRecyclerView?.scrollState == SCROLL_STATE_DRAGGING) return
        searchActive = hasFocus && prefs.drawerSuggestionTabs.firstCached()
        update()
        if (searchActive) load()
    }

    override fun onStateTransitionComplete(finalState: LauncherState) {
        // Also ends tabs kept after a drag, once the drawer is closed.
        if (finalState != LauncherState.ALL_APPS && searchActive) {
            searchActive = false
            update()
        }
    }

    override fun onAppsUpdated() = load()

    // More or fewer apps per tab, and new icon views.
    override fun onDeviceProfileChanged(dp: DeviceProfile) = load()

    private fun load() {
        val launcher = launcher ?: return
        if (!prefs.drawerSuggestionTabs.firstCached()) return
        val apps = launcher.appsView.appsStore.apps
        val limit = launcher.deviceProfile.numShownAllAppsColumns * maxRows(launcher.deviceProfile)
        loadJob?.cancel()
        loadJob = launcher.lifecycleScope.launch {
            suggestions = withContext(Dispatchers.IO) { loadAppSuggestions(context, apps, limit) }
            update()
        }
    }

    /** Shows the selected tab while search is active and any tab has apps. */
    private fun update() {
        val launcher = launcher ?: return
        val dp = launcher.deviceProfile
        columns = dp.numShownAllAppsColumns.coerceAtLeast(1)
        val mostApps = suggestions.maxOfOrNull { it.size } ?: 0
        rows = ((mostApps + columns - 1) / columns).coerceAtMost(maxRows(dp))
        // Until the user picks a tab, open on the first one with apps.
        if (!tabPicked) selectedTab = suggestions.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0)
        val apps = suggestions.getOrNull(selectedTab).orEmpty()
        tabs.updateTabTextColor(selectedTab)
        bindIcons(launcher, apps, columns * rows)
        emptyText.isVisible = apps.isEmpty()

        val shown = searchActive && rows > 0
        // Not over INVISIBLE, which is how a row scrolled out of view is hidden.
        if (!shown) {
            visibility = GONE
        } else if (visibility == GONE) {
            visibility = VISIBLE
        }
        requestLayout()
        val header = parent as? FloatingHeaderView ?: return
        if (getExpectedHeight() != reportedHeight) {
            reportedHeight = getExpectedHeight()
            header.findFixedRowByType(PredictionRowView::class.java)?.setHiddenBySuggestionTabs(shown)
            // Lays out the header and the lists below again, scrolled to the top.
            header.onHeightUpdated()
        }
    }

    private fun bindIcons(launcher: LawnchairLauncher, apps: List<AppInfo>, count: Int) {
        // Icons take their size from the device profile when they are created.
        if (iconsProfile !== launcher.deviceProfile) {
            removeViews(FIXED_CHILDREN, childCount - FIXED_CHILDREN)
            iconsProfile = launcher.deviceProfile
        }
        while (childCount > FIXED_CHILDREN + count) removeViewAt(childCount - 1)
        while (childCount < FIXED_CHILDREN + count) addView(createIcon(launcher))
        for (i in 0 until count) {
            val icon = getChildAt(FIXED_CHILDREN + i) as BubbleTextView
            icon.reset()
            icon.isInvisible = i >= apps.size
            if (i < apps.size) icon.applyFromApplicationInfo(apps[i])
        }
    }

    /** The same icon view as the A-Z list (BaseAllAppsAdapter). */
    private fun createIcon(launcher: LawnchairLauncher): BubbleTextView {
        val layout = if (launcher.deviceProfile.inv.enableTwoLinesInAllApps) R.layout.all_apps_icon_twoline else R.layout.all_apps_icon
        return (launcher.appsView.layoutInflater.inflate(layout, this, false) as BubbleTextView).apply {
            setOnClickListener(launcher.itemOnClickListener)
            setOnLongClickListener(launcher.allAppsItemLongClickListener)
            setLongPressTimeoutFactor(1f)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // The strip narrows itself to the outer icon columns (alignOnIcon).
        tabs.measure(exactly(width), exactly(tabsHeight))
        emptyText.measure(exactly(width), exactly(rows * cellHeight()))
        for (i in FIXED_CHILDREN until childCount) {
            val column = (i - FIXED_CHILDREN) % columns
            getChildAt(i).measure(exactly(cellLeft(column + 1, width) - cellLeft(column, width)), exactly(cellHeight()))
        }
        setMeasuredDimension(width, getExpectedHeight())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val tabsLeft = (width - tabs.measuredWidth) / 2
        tabs.layout(tabsLeft, tabsTop, tabsLeft + tabs.measuredWidth, tabsTop + tabsHeight)
        val top = tabsTop + tabsHeight
        emptyText.layout(0, top, width, top + emptyText.measuredHeight)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in FIXED_CHILDREN until childCount) {
            val index = i - FIXED_CHILDREN
            val column = if (rtl) columns - 1 - index % columns else index % columns
            val y = top + index / columns * cellHeight()
            getChildAt(i).layout(cellLeft(column, width), y, cellLeft(column + 1, width), y + cellHeight())
        }
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun cellLeft(column: Int, width: Int) = width * column / columns

    private fun cellHeight() = launcher?.deviceProfile?.allAppsProfile?.cellHeightPx ?: 0

    // FloatingHeaderRow

    override fun setup(headerView: FloatingHeaderView, allRows: Array<FloatingHeaderRow>, tabsHidden: Boolean) = Unit

    override fun getExpectedHeight(): Int = if (visibility == GONE) 0 else tabsTop + tabsHeight + rows * cellHeight()

    override fun shouldDraw(): Boolean = visibility != GONE

    override fun hasVisibleContent(): Boolean = visibility != GONE

    override fun setVerticalScroll(scroll: Int, isScrolledOut: Boolean) {
        // Like the suggested apps row: follow the list, and hide once scrolled out.
        if (!isScrolledOut) translationY = scroll.toFloat()
        alpha = if (isScrolledOut) 0f else 1f
        if (visibility != GONE) AlphaUpdateListener.updateVisibility(this)
    }

    override fun getTypeClass(): Class<out FloatingHeaderRow> = AppSuggestionsHeaderView::class.java

    override fun hasOverlappingRendering() = false

    private companion object {
        const val FIXED_CHILDREN = 2

        /** Two rows of icons, or one on a phone in landscape, where the screen is short. */
        fun maxRows(dp: DeviceProfile) = if (dp.deviceProperties.isPhone && dp.deviceProperties.isLandscape) 1 else 2
    }
}
