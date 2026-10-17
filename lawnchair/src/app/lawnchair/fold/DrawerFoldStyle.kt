package app.lawnchair.fold

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.FloatingHeaderView
import com.android.launcher3.appprediction.PredictionRowView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The fold transition of the app drawer, played instead of the home style while the drawer is open.
 * The engine picks one subclass per [FoldDrawerLayout]: [DrawerRiverStyle] (EXPAND),
 * [DrawerBookStyle] (PAGES) and [DrawerZoomStyle] (BIG_ICONS).
 *
 * The anchor: what the eye is on when the device opens does not move; everything else flows around
 * it, in the list's order. The "before" of the large layout is the cover's drawer, rebuilt from the
 * cover profile around the list's first visible item (the drawer keeps its scroll across the
 * change, see [FoldDrawerState]), so the same path plays for a real unfold, a fold tracked on the
 * inner display and the debug replay. [appsView] is the open drawer and [dp] the launcher's
 * profile when the run starts; [capture] is what the drawer showed right before the profile change
 * that started the run (null for the other runs).
 *
 * Shared by every layout: the search bar only stretches (or contracts) between its two widths;
 * while searching nothing else moves, and the query, results and scroll stay. For the layouts that
 * move them ([prepareHeader]), the header rows go from the cover's header to the large one: the
 * predictions slide from their cover columns to their new ones (the extra ones fade in from the
 * hinge side) and the personal/work tabs stretch. Folding plays the same path backwards: on the
 * inner display when the fold is tracked, else on the cover after the switch.
 */
@RequiresApi(Build.VERSION_CODES.Q)
abstract class DrawerFoldStyle(
    private val appsView: ActivityAllAppsContainerView<*>,
    private val dp: DeviceProfile,
    protected val capture: FoldDrawerState.Capture?,
) : FoldStyle() {

    protected var list: AllAppsRecyclerView? = null
    protected var header: FloatingHeaderView? = null
    private var bar: View? = null
    private var barStretch: FoldStretch? = null
    protected var searching = false
    private var prepared = false

    // Profiles and the mapping between cover and large-screen coordinates
    protected var cover: DeviceProfile? = null
    protected var large: DeviceProfile? = null

    /** Whether the launcher shows the large profile (not when a replay plays the unfold on the cover). */
    protected var onLarge = false
    private var fixedLeft = 0f
    protected var coverScale = 1f
    protected var movingLeft = true
    protected var rtl = false

    // Header rows: prediction icons slide, the tabs stretch
    private val predictionViews = ArrayList<View>()
    private var predictionFrom = FloatArray(0)
    private var predictionTo = FloatArray(0)
    private var predictionEnterX = 0f
    private var tabsStretch: FoldStretch? = null
    private var headerActive = false

    protected val coords = FloatArray(2)

    override fun onStart(scene: FoldScene): Boolean {
        searching = appsView.isSearching
        list = appsView.activeRecyclerView
        header = appsView.floatingHeaderView
        bar = appsView.searchView
        rtl = Utilities.isRtl(appsView.resources)
        val cover = FoldLayout.coverProfileOf(dp)
        val large = FoldLayout.largeProfileOf(dp)
        this.cover = cover
        this.large = large
        onLarge = dp === large
        if (large != null && cover != null) {
            val width = large.deviceProperties.widthPx
            movingLeft = FoldPosture.isMovingHalfLeft(large.deviceProperties.rotationHint) ?: false
            fixedLeft = if (movingLeft) width / 2f else 0f
            coverScale = FoldLayout.coverScaleOnLarge(cover, large)
        }
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val list = list ?: return
        if (!prepared) {
            prepared = true
            prepareBar(scene)
            if (!searching) prepare(scene, list)
        }
        // 0 = the other screen's look, 1 = this layout at rest
        val p = progress.coerceIn(0f, 1f)
        val t = if (scene.largeLayout) p else 1f - p
        barStretch?.apply(FoldTunables.DRAWER_EASE.getInterpolation(t))
        if (headerActive) applyHeader(t)
        apply(scene, list, t)
    }

    /** Called once, on the first frame, unless searching: sets up the layout's own motion. */
    protected abstract fun prepare(scene: FoldScene, list: AllAppsRecyclerView)

    /** Called every frame: the layout's own motion at [t] (0 = the other screen's look, 1 = at rest). */
    protected abstract fun apply(scene: FoldScene, list: AllAppsRecyclerView, t: Float)

    /** Puts the bar and the header rows back. Subclasses restore their own motion first. */
    override fun onEnd(scene: FoldScene) {
        barStretch?.restore()
        barStretch = null
        if (headerActive) {
            for (i in predictionViews.indices) {
                val view = predictionViews[i]
                (view as? Reorderable)?.translateDelegate?.setTranslation(FoldItem.TRANSLATE_INDEX, 0f, 0f)
                view.alpha = 1f
            }
            tabsStretch?.restore()
        }
        predictionViews.clear()
        tabsStretch = null
        headerActive = false
    }

    // Search bar

    private fun prepareBar(scene: FoldScene) {
        val bar = bar as? ViewGroup ?: return
        val other = otherProfile(scene) ?: return
        if (cover == null || large == null || bar.width == 0) return
        locate(scene, bar)
        val barLeft = coords[0]
        val barRight = barLeft + bar.width
        // The other screen's bar on this layout: as captured, else rebuilt from its profile
        val captured = capture?.takeIf { !it.searchBarLeft.isNaN() && it.width == other.deviceProperties.widthPx }
        val padding = if (captured != null) 0f else FoldDrawerLayouts.getSearchBarPadding(appsView.context, other) * if (isSearchActive()) 0.5f else 1f
        var fromLeft = fromOther(scene, captured?.searchBarLeft ?: padding)
        var fromRight = fromOther(scene, captured?.searchBarRight ?: (other.deviceProperties.widthPx - padding))
        if (fromRight < fromLeft) {
            val swap = fromLeft
            fromLeft = fromRight
            fromRight = swap
        }
        val stretch = FoldStretch(bar, (fromLeft - barLeft).roundToInt().coerceAtLeast(0), (barRight - fromRight).roundToInt().coerceAtLeast(0))
        if (stretch.capture()) barStretch = stretch
    }

    private fun isSearchActive(): Boolean {
        val bar = bar ?: return false
        return searching || bar.hasFocus()
    }

    // Header rows

    /**
     * The predictions row and the tabs between the cover's header and the large one. The header's
     * content box of the other screen is rebuilt from that screen's profile (the header is padded
     * by the drawer's side paddings, see FloatingHeaderView#setInsets) and mapped onto this one.
     */
    protected fun prepareHeader(scene: FoldScene) {
        val header = header ?: return
        val cover = cover ?: return
        val large = large ?: return
        if (!header.isShown || header.width == 0 || scene.largeLayout != onLarge) return
        locate(scene, header)
        val contentLeft = coords[0] + header.paddingLeft
        val contentRight = coords[0] + header.width - header.paddingRight
        val other = if (scene.largeLayout) cover else large
        val otherPadding = (other.allAppsPadding.left + other.allAppsPadding.right).toFloat()
        val otherLeft = fromOther(scene, otherPadding)
        val otherRight = fromOther(scene, other.deviceProperties.widthPx - otherPadding)

        val row = header.findFixedRowByType(PredictionRowView::class.java)
        if (row != null && row.isShown && row.childCount > 0) {
            locate(scene, row)
            val rowLeft = coords[0] + row.paddingLeft
            val rowRight = coords[0] + row.width - row.paddingRight
            val boxLeft = otherLeft + (rowLeft - contentLeft)
            val boxRight = max(boxLeft, otherRight - (contentRight - rowRight))
            val otherColumns = other.numShownAllAppsColumns.coerceAtLeast(1)
            val cell = (boxRight - boxLeft) / otherColumns
            val count = row.childCount
            predictionFrom = FloatArray(count)
            predictionTo = FloatArray(count)
            for (i in 0 until count) {
                val child = row.getChildAt(i)
                predictionViews.add(child)
                locate(scene, child, center = true)
                predictionTo[i] = coords[0]
                predictionFrom[i] = when {
                    i >= otherColumns -> Float.NaN
                    rtl -> boxRight - (i + 0.5f) * cell
                    else -> boxLeft + (i + 0.5f) * cell
                }
            }
            // Predictions the cover did not show come in from its row's hinge-side end
            predictionEnterX = if (movingLeft) boxLeft else boxRight
            headerActive = true
        }

        val tabs = header.findViewById<View>(R.id.tabs) as? ViewGroup
        if (tabs != null && tabs.isShown && tabs.width > 0) {
            locate(scene, tabs)
            val tabsLeft = coords[0]
            val tabsRight = tabsLeft + tabs.width
            val fromLeft = otherLeft + (tabsLeft + tabs.paddingLeft - contentLeft)
            val fromRight = otherRight - (contentRight - (tabsRight - tabs.paddingRight))
            val stretch = FoldStretch(tabs, (fromLeft - tabsLeft).roundToInt().coerceAtLeast(0), (tabsRight - fromRight).roundToInt().coerceAtLeast(0))
            if (stretch.capture()) {
                tabsStretch = stretch
                headerActive = true
            }
        }
    }

    private fun applyHeader(t: Float) {
        // Like the river's row 1: straight and right away
        val e = FoldTunables.DRAWER_EASE.getInterpolation(t)
        for (i in predictionViews.indices) {
            val view = predictionViews[i]
            val from = predictionFrom[i]
            val entering = from.isNaN()
            val dx = ((if (entering) predictionEnterX else from) - predictionTo[i]) * (1f - e)
            (view as? Reorderable)?.translateDelegate?.setTranslation(FoldItem.TRANSLATE_INDEX, dx, 0f)
            if (entering) view.alpha = e
        }
        tabsStretch?.apply(e)
    }

    // Coordinates

    /** The other screen's profile: the cover's on the large layout, the large one on the cover. */
    private fun otherProfile(scene: FoldScene): DeviceProfile? = if (scene.largeLayout) cover else large

    /** An x of the other screen's window, on this layout. */
    private fun fromOther(scene: FoldScene, x: Float): Float = if (scene.largeLayout) toLarge(x) else toCover(x)

    /** Sets [coords] to [view]'s top left corner, or its [center], in DragLayer coordinates and without transforms. */
    protected fun locate(scene: FoldScene, view: View, center: Boolean = false) {
        coords[0] = if (center) view.width / 2f else 0f
        coords[1] = if (center) view.height / 2f else 0f
        Utilities.getDescendantCoordRelativeToAncestor(view, scene.dragLayer, coords, false, true)
    }

    /** Cover x (cover window) to the large screen, where the cover maps onto the fixed half. */
    protected fun toLarge(x: Float): Float = fixedLeft + x * coverScale

    /** Large-screen x to the cover window. */
    protected fun toCover(x: Float): Float = (x - fixedLeft) / max(coverScale, 0.01f)
}
