package app.lawnchair.fold

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.android.app.animation.Interpolators
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.FloatingHeaderView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.util.ScrollableLayoutManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The fold transition of the app drawer, played instead of the home style while the drawer is open.
 *
 * The anchor: what the eye is on when the device opens does not move; everything else flows around
 * it, in the list's order. The "before" of the large layout is the cover's drawer, rebuilt from the
 * cover profile around the list's first visible item (the drawer keeps its scroll across the
 * change, see [FoldDrawerState]), so the same path plays for a real unfold, a fold tracked on the
 * inner display and the debug replay. [capture] is what the drawer showed right before the profile
 * change that started the run (null for the other runs).
 *
 * - [FoldDrawerLayout.EXPAND], "river": the first row goes straight to its new place; the apps after
 *   it follow one after another ([FoldTunables.DRAWER_RIVER_STAGGER_MILLIS] apart) along a small arc,
 *   the ones that were below the cover's bottom edge flowing up into the new half.
 * - [FoldDrawerLayout.PAGES], "book": the fixed page does not move at all; the apps of the second
 *   page rise from below its bottom edge, top row first.
 * - [FoldDrawerLayout.BIG_ICONS], "zoom": the grid scales from the cover's size up to the full
 *   width, anchored at the outer edge of the fixed half, so it grows across the hinge.
 *
 * The header rows follow: in EXPAND the predictions slide with row 1 from their cover columns to their
 * new ones (the extra ones fade in from the hinge side) and the personal/work tabs stretch; in
 * BIG_ICONS they zoom with the grid; in PAGES they stay on the fixed half, so they do not move.
 *
 * The search bar only stretches (or contracts) between its two widths; while searching nothing else
 * moves, and the query, results and scroll stay. Folding plays the same path backwards: on the inner
 * display when the fold is tracked, else on the cover after the switch (river from the captured
 * large positions, zoom from the large size, nothing for the book).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DrawerFoldStyle(
    private val layout: FoldDrawerLayout,
    private val capture: FoldDrawerState.Capture?,
) : FoldStyle() {

    private var list: AllAppsRecyclerView? = null
    private var header: FloatingHeaderView? = null
    private var bar: View? = null
    private var searching = false
    private var prepared = false

    // Profiles and the mapping between cover and large-screen coordinates
    private var cover: DeviceProfile? = null
    private var large: DeviceProfile? = null
    private var fixedLeft = 0f
    private var coverScale = 1f
    private var movingLeft = true
    private var rtl = false

    // Search bar padding, from and to; the bar's content is stretched between them
    private var barFromLeft = 0
    private var barFromRight = 0
    private var barToLeft = 0
    private var barToRight = 0
    private var barActive = false
    private var barStretch: FoldStretch? = null

    // River: the cover's grid position of each adapter position
    private var coverColumn = IntArray(0)
    private var coverRow = IntArray(0)
    private var coverOrdinal = IntArray(0)
    private var anchorPosition = RecyclerView.NO_POSITION
    private var anchorCenterY = 0f
    private var coverPaddingLeft = 0f
    private var coverCellWidth = 0f
    private var coverCellHeight = 0f
    private var coverColumns = 1
    private var firstRiverOrdinal = 0
    private var stagger = 0f
    private var arcPx = 0f
    private var useCaptured = false
    private var riverActive = false

    /** Every item view the river moved, also those recycled since (see [onEnd]). */
    private val riverMoved = ArrayList<View>()

    // Header rows (EXPAND): prediction icons slide, the tabs stretch
    private val predictionViews = ArrayList<View>()
    private var predictionFrom = FloatArray(0)
    private var predictionTo = FloatArray(0)
    private var predictionEnterX = 0f
    private var tabs: View? = null
    private var tabsStretch: FoldStretch? = null
    private var tabsFromLeft = 0
    private var tabsFromRight = 0
    private var tabsToLeft = 0
    private var tabsToRight = 0
    private var headerActive = false

    // Book
    private var book: FoldDrawerBook? = null

    // Zoom
    private var zoomFrom = 1f
    private var zoomActive = false
    private var zoomPivotRootX = 0f
    private var zoomPivotRootY = 0f
    private var savedClipChildren = true

    private val coords = FloatArray(2)

    override fun onStart(scene: FoldScene): Boolean {
        val launcher = scene.launcher
        val appsView = launcher.appsView ?: return false
        searching = appsView.isSearching
        list = appsView.activeRecyclerView
        header = appsView.floatingHeaderView
        bar = appsView.searchView
        rtl = Utilities.isRtl(launcher.resources)
        val dp = launcher.deviceProfile
        cover = FoldLayout.coverProfileOf(dp)
        large = FoldLayout.largeProfileOf(dp)
        val large = large
        val cover = cover
        if (large != null && cover != null) {
            val width = large.deviceProperties.widthPx
            movingLeft = FoldPosture.isMovingHalfLeft(large.deviceProperties.rotationHint) ?: false
            fixedLeft = if (movingLeft) width / 2f else 0f
            coverScale = FoldLayout.coverScaleOnLarge(cover, large)
        }
        // The zoom needs more rows laid out below before the first frame, so its first, smallest
        // frame is full.
        if (!searching && layout == FoldDrawerLayout.BIG_ICONS && scene.largeLayout && dp === large && cover != null) {
            zoomFrom = zoomScale(cover, large)
            val height = large.deviceProperties.heightPx
            (list?.layoutManager as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(
                (height * (1f / zoomFrom - 1f)).roundToInt().coerceAtLeast(0),
            )
        }
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val launcher = scene.launcher
        val list = list ?: return
        if (!prepared) {
            prepared = true
            prepareBar(scene)
            if (!searching) {
                val onLarge = launcher.deviceProfile === large
                when (layout) {
                    FoldDrawerLayout.EXPAND -> {
                        prepareRiver(scene, list, onLarge)
                        prepareHeader(scene, onLarge)
                    }

                    FoldDrawerLayout.PAGES -> if (onLarge && scene.largeLayout) {
                        book = FoldDrawerBook.of(list)?.takeIf { it.isActive }
                    }

                    FoldDrawerLayout.BIG_ICONS -> prepareZoom(scene, list, onLarge)
                }
            }
        }
        // 0 = the other screen's look, 1 = this layout at rest
        val t = if (scene.largeLayout) p else 1f - p
        book?.let {
            it.transitionProgress = p
            it.update()
        }
        applyBar(t)
        if (headerActive) applyHeader(t)
        if (riverActive) applyRiver(scene, list, t)
        if (zoomActive) applyZoom(scene, list, t)
    }

    override fun onEnd(scene: FoldScene) {
        // First, so the book drives itself again whatever happens below
        book?.let {
            it.transitionProgress = Float.NaN
            it.update()
        }
        book = null
        if (barActive) barStretch?.restore()
        barActive = false
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
        val list = list
        if (list != null) {
            if (riverActive) {
                // Not only the attached children: a view recycled mid-transition (into the pool or
                // the view cache) is bound again without its translation being reset.
                for (i in riverMoved.indices) {
                    val view = riverMoved[i]
                    view.translationX = 0f
                    view.translationY = 0f
                }
            }
            if (zoomActive) {
                resetZoom(list)
                resetZoom(header)
                list.clipChildren = savedClipChildren
            }
            if (layout == FoldDrawerLayout.BIG_ICONS) {
                (list.layoutManager as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(0)
            }
        }
        riverActive = false
        riverMoved.clear()
        zoomActive = false
    }

    // Search bar

    private fun prepareBar(scene: FoldScene) {
        val bar = bar ?: return
        val cover = cover ?: return
        val large = large ?: return
        if (bar.width == 0) return
        barToLeft = bar.paddingLeft
        barToRight = bar.paddingRight
        locate(scene, bar)
        val barLeft = coords[0]
        val barRight = barLeft + bar.width
        val captured = capture?.takeIf { !it.searchBarLeft.isNaN() }
        var fromLeft: Float
        var fromRight: Float
        if (scene.largeLayout) {
            // The cover's bar, on the fixed half
            if (captured != null && captured.width == cover.deviceProperties.widthPx) {
                fromLeft = toLarge(captured.searchBarLeft)
                fromRight = toLarge(captured.searchBarRight)
            } else {
                val padding = FoldDrawerLayouts.getSearchBarPadding(scene.launcher, cover) * if (isSearchActive()) 0.5f else 1f
                fromLeft = toLarge(padding)
                fromRight = toLarge(cover.deviceProperties.widthPx - padding)
            }
        } else {
            // The large bar, seen from the cover
            if (captured != null && captured.width == large.deviceProperties.widthPx) {
                fromLeft = toCover(captured.searchBarLeft)
                fromRight = toCover(captured.searchBarRight)
            } else {
                val padding = FoldDrawerLayouts.getSearchBarPadding(scene.launcher, large) * if (isSearchActive()) 0.5f else 1f
                fromLeft = toCover(padding)
                fromRight = toCover(large.deviceProperties.widthPx - padding)
            }
        }
        if (fromRight < fromLeft) {
            val swap = fromLeft
            fromLeft = fromRight
            fromRight = swap
        }
        barFromLeft = (fromLeft - barLeft).roundToInt().coerceAtLeast(0)
        barFromRight = (barRight - fromRight).roundToInt().coerceAtLeast(0)
        if (barFromLeft == barToLeft && barFromRight == barToRight) return
        val stretch = (bar as? ViewGroup)?.let { FoldStretch(it) } ?: return
        barActive = stretch.capture()
        barStretch = stretch
    }

    private fun applyBar(t: Float) {
        val bar = bar ?: return
        val stretch = barStretch ?: return
        if (!barActive) return
        val e = EASE.getInterpolation(t)
        val left = (barFromLeft + (barToLeft - barFromLeft) * e).roundToInt()
        val right = (barFromRight + (barToRight - barFromRight) * e).roundToInt()
        stretch.apply(left, bar.width - right)
    }

    private fun isSearchActive(): Boolean {
        val bar = bar ?: return false
        return searching || bar.hasFocus()
    }

    // Header rows (EXPAND)

    /**
     * The predictions row and the tabs between the cover's header and the large one. The header's
     * content box of the other screen is rebuilt from that screen's profile (the header is padded
     * by the drawer's side paddings, see FloatingHeaderView#setInsets) and mapped onto this one.
     */
    private fun prepareHeader(scene: FoldScene, onLarge: Boolean) {
        val header = header ?: return
        val cover = cover ?: return
        val large = large ?: return
        if (!header.isShown || header.width == 0 || scene.largeLayout != onLarge) return
        locate(scene, header)
        val contentLeft = coords[0] + header.paddingLeft
        val contentRight = coords[0] + header.width - header.paddingRight
        val other = if (scene.largeLayout) cover else large
        val otherPadding = (other.allAppsPadding.left + other.allAppsPadding.right).toFloat()
        val otherWidth = other.deviceProperties.widthPx.toFloat()
        val otherLeft = if (scene.largeLayout) toLarge(otherPadding) else toCover(otherPadding)
        val otherRight = if (scene.largeLayout) toLarge(otherWidth - otherPadding) else toCover(otherWidth - otherPadding)

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

        val tabs = header.findViewById<View>(R.id.tabs)
        if (tabs != null && tabs.isShown && tabs.width > 0) {
            locate(scene, tabs)
            val tabsLeft = coords[0]
            val tabsRight = tabsLeft + tabs.width
            tabsToLeft = tabs.paddingLeft
            tabsToRight = tabs.paddingRight
            val fromLeft = otherLeft + (tabsLeft + tabsToLeft - contentLeft)
            val fromRight = otherRight - (contentRight - (tabsRight - tabsToRight))
            tabsFromLeft = (fromLeft - tabsLeft).roundToInt().coerceAtLeast(0)
            tabsFromRight = (tabsRight - fromRight).roundToInt().coerceAtLeast(0)
            val stretch = (tabs as? ViewGroup)?.let { FoldStretch(it) }
            if ((tabsFromLeft != tabsToLeft || tabsFromRight != tabsToRight) && stretch != null && stretch.capture()) {
                this.tabs = tabs
                tabsStretch = stretch
                headerActive = true
            }
        }
    }

    private fun applyHeader(t: Float) {
        // Like row 1 of the river: straight and right away
        val e = EASE.getInterpolation(t)
        for (i in predictionViews.indices) {
            val view = predictionViews[i]
            val from = predictionFrom[i]
            val entering = from.isNaN()
            val dx = ((if (entering) predictionEnterX else from) - predictionTo[i]) * (1f - e)
            (view as? Reorderable)?.translateDelegate?.setTranslation(FoldItem.TRANSLATE_INDEX, dx, 0f)
            if (entering) view.alpha = e
        }
        val tabs = tabs ?: return
        val stretch = tabsStretch ?: return
        val left = (tabsFromLeft + (tabsToLeft - tabsFromLeft) * e).roundToInt()
        val right = (tabsFromRight + (tabsToRight - tabsFromRight) * e).roundToInt()
        stretch.apply(left, tabs.width - right)
    }

    // River (EXPAND)

    private fun prepareRiver(scene: FoldScene, list: AllAppsRecyclerView, onLarge: Boolean) {
        arcPx = scene.dpToPx(FoldTunables.DRAWER_RIVER_ARC_DP)
        if (scene.largeLayout) {
            if (onLarge) prepareRiverFromCover(scene, list)
        } else {
            prepareRiverFromLarge(list)
        }
    }

    /** Large layout: each item comes from where the cover's drawer shows it. */
    private fun prepareRiverFromCover(scene: FoldScene, list: AllAppsRecyclerView) {
        val cover = cover ?: return
        val items = list.apps?.adapterItems ?: return
        // The cover's grid, row by row as the cover lays it out (rows restart after dividers)
        coverColumns = cover.numShownAllAppsColumns.coerceAtLeast(1)
        val size = items.size
        coverColumn = IntArray(size) { -1 }
        coverRow = IntArray(size)
        coverOrdinal = IntArray(size) { -1 }
        var row = -1
        var inSection = 0
        var ordinal = 0
        for (i in 0 until size) {
            if (BaseAllAppsAdapter.isIconViewType(items[i].viewType)) {
                if (inSection % coverColumns == 0) row++
                coverColumn[i] = inSection % coverColumns
                coverOrdinal[i] = ordinal++
                inSection++
            } else {
                inSection = 0
            }
            coverRow[i] = row
        }
        val coverWidth = cover.deviceProperties.widthPx.toFloat()
        coverPaddingLeft = cover.allAppsPadding.left.toFloat()
        coverCellWidth = (coverWidth - cover.allAppsPadding.left - cover.allAppsPadding.right) / coverColumns
        coverCellHeight = cover.allAppsProfile.cellHeightPx.toFloat()
        // The anchor: the item the cover showed first (the list keeps it on top), else the first
        // visible icon.
        val captured = capture?.anchorPosition ?: RecyclerView.NO_POSITION
        anchorPosition = RecyclerView.NO_POSITION
        var firstIcon: View? = null
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val position = gridPosition(list, child)
            if (position == RecyclerView.NO_POSITION) continue
            if (firstIcon == null) firstIcon = child
            if (position == captured) {
                anchorPosition = position
                anchorCenterY = layoutCenterY(scene, child)
            }
        }
        if (anchorPosition == RecyclerView.NO_POSITION) {
            val first = firstIcon ?: return
            anchorPosition = list.getChildAdapterPosition(first)
            anchorCenterY = layoutCenterY(scene, first)
        }
        // Row 1 goes straight; the apps after it follow in order.
        val anchorRow = coverRow[anchorPosition]
        firstRiverOrdinal = Int.MAX_VALUE
        var movers = 0
        for (i in 0 until list.childCount) {
            val position = gridPosition(list, list.getChildAt(i))
            if (position == RecyclerView.NO_POSITION || coverRow[position] <= anchorRow) continue
            firstRiverOrdinal = minOf(firstRiverOrdinal, coverOrdinal[position])
            movers++
        }
        stagger = staggerFor(movers)
        useCaptured = false
        riverActive = true
    }

    /** Cover after a fold: each item comes back from where the large drawer showed it. */
    private fun prepareRiverFromLarge(list: AllAppsRecyclerView) {
        val large = large ?: return
        val capture = capture ?: return
        if (capture.itemCenters.size() == 0 || capture.width != large.deviceProperties.widthPx) return
        var movers = 0
        for (i in 0 until list.childCount) {
            val position = list.getChildAdapterPosition(list.getChildAt(i))
            if (position != RecyclerView.NO_POSITION && capture.itemCenters[position] != null) movers++
        }
        stagger = staggerFor(movers)
        useCaptured = true
        riverActive = movers > 0
    }

    /** The adapter position of an icon or folder of the cover grid, else [RecyclerView.NO_POSITION]. */
    private fun gridPosition(list: AllAppsRecyclerView, child: View): Int {
        val position = list.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION || position >= coverColumn.size || coverColumn[position] < 0) {
            return RecyclerView.NO_POSITION
        }
        return position
    }

    private fun staggerFor(movers: Int): Float {
        val nominal = FoldTunables.DRAWER_RIVER_STAGGER_MILLIS / FoldTunables.UNFOLD_DURATION_MILLIS.coerceAtLeast(1L).toFloat()
        return if (movers <= 1) 0f else minOf(nominal, MAX_DELAY / (movers - 1))
    }

    private fun applyRiver(scene: FoldScene, list: AllAppsRecyclerView, t: Float) {
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val position = list.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            locate(scene, child, center = true)
            val toX = coords[0]
            val toY = coords[1]
            val fromX: Float
            val fromY: Float
            val delay: Float
            if (useCaptured) {
                val center = capture?.itemCenters?.get(position) ?: continue
                fromX = toCover(center[0])
                fromY = center[1]
                delay = (i * stagger).coerceAtMost(MAX_DELAY)
            } else {
                if (gridPosition(list, child) == RecyclerView.NO_POSITION) continue
                val column = if (rtl) coverColumns - 1 - coverColumn[position] else coverColumn[position]
                fromX = toLarge(coverPaddingLeft + (column + 0.5f) * coverCellWidth)
                fromY = anchorCenterY + (coverRow[position] - coverRow[anchorPosition]) * coverCellHeight
                val rowOne = coverRow[position] <= coverRow[anchorPosition]
                delay = if (rowOne) 0f else ((coverOrdinal[position] - firstRiverOrdinal + 1) * stagger).coerceAtMost(MAX_DELAY)
            }
            val local = ((t - delay) / (1f - MAX_DELAY)).coerceIn(0f, 1f)
            val e = EASE.getInterpolation(local)
            val dx = fromX - toX
            val dy = fromY - toY
            var x = dx * (1f - e)
            var y = dy * (1f - e)
            // A small arc, bulging up, along the way
            val length = hypot(dx, dy)
            if (length > 1f) {
                var nx = -dy / length
                var ny = dx / length
                if (ny > 0f) {
                    nx = -nx
                    ny = -ny
                }
                val bulge = arcPx * 4f * e * (1f - e) * (length / (length + arcPx * ARC_REFERENCE))
                x += nx * bulge
                y += ny * bulge
            }
            if (child.translationX == 0f && child.translationY == 0f && !riverMoved.contains(child)) riverMoved.add(child)
            if (abs(child.translationX - x) > 0.1f) child.translationX = x
            if (abs(child.translationY - y) > 0.1f) child.translationY = y
        }
    }

    // Zoom (BIG_ICONS)

    private fun prepareZoom(scene: FoldScene, list: AllAppsRecyclerView, onLarge: Boolean) {
        val cover = cover ?: return
        val large = large ?: return
        val outerEdge: Float
        if (scene.largeLayout) {
            if (!onLarge) return
            zoomFrom = zoomScale(cover, large)
            outerEdge = if (movingLeft) large.deviceProperties.widthPx.toFloat() else 0f
        } else {
            zoomFrom = 1f / zoomScale(cover, large)
            outerEdge = if (movingLeft) cover.deviceProperties.widthPx.toFloat() else 0f
        }
        if (abs(zoomFrom - 1f) < 0.01f) return
        // Pivot: the outer edge of the fixed half, at the top of the drawer content
        val top = header ?: list
        locate(scene, top)
        zoomPivotRootX = outerEdge
        zoomPivotRootY = coords[1]
        savedClipChildren = list.clipChildren
        list.clipChildren = false
        zoomActive = true
    }

    private fun applyZoom(scene: FoldScene, list: AllAppsRecyclerView, t: Float) {
        val scale = zoomFrom + (1f - zoomFrom) * EASE.getInterpolation(t)
        zoomView(scene, list, scale)
        zoomView(scene, header, scale)
    }

    private fun zoomView(scene: FoldScene, view: View?, scale: Float) {
        view ?: return
        locate(scene, view)
        val pivotX = zoomPivotRootX - coords[0]
        val pivotY = zoomPivotRootY - coords[1]
        if (view.pivotX != pivotX) view.pivotX = pivotX
        if (view.pivotY != pivotY) view.pivotY = pivotY
        if (view.scaleX != scale) view.scaleX = scale
        if (view.scaleY != scale) view.scaleY = scale
    }

    private fun resetZoom(view: View?) {
        view ?: return
        view.scaleX = 1f
        view.scaleY = 1f
        view.resetPivot()
    }

    /** Scale that shrinks the large grid onto the cover's grid on the fixed half. */
    private fun zoomScale(cover: DeviceProfile, large: DeviceProfile): Float {
        val coverGrid = (cover.deviceProperties.widthPx - cover.allAppsPadding.left - cover.allAppsPadding.right) * coverScale
        val largeGrid = (large.deviceProperties.widthPx - large.allAppsPadding.left - large.allAppsPadding.right).toFloat()
        return if (largeGrid > 0f) (coverGrid / largeGrid).coerceIn(0.2f, 1f) else 1f
    }

    // Coordinates

    private fun layoutCenterY(scene: FoldScene, child: View): Float {
        locate(scene, child, center = true)
        return coords[1]
    }

    /** Sets [coords] to [view]'s top left corner, or its [center], in DragLayer coordinates and without transforms. */
    private fun locate(scene: FoldScene, view: View, center: Boolean = false) {
        coords[0] = if (center) view.width / 2f else 0f
        coords[1] = if (center) view.height / 2f else 0f
        Utilities.getDescendantCoordRelativeToAncestor(view, scene.dragLayer, coords, false, true)
    }

    /** Cover x (cover window) to the large screen, where the cover maps onto the fixed half. */
    private fun toLarge(x: Float): Float = fixedLeft + x * coverScale

    /** Large-screen x to the cover window. */
    private fun toCover(x: Float): Float = (x - fixedLeft) / max(coverScale, 0.01f)

    private companion object {
        /** Share of the progress the last item of a river waits before it moves. */
        const val MAX_DELAY = 0.5f

        /** Arcs of long paths bulge the full [FoldTunables.DRAWER_RIVER_ARC_DP]; short ones less. */
        const val ARC_REFERENCE = 2f

        val EASE = Interpolators.EMPHASIZED_DECELERATE
    }
}
