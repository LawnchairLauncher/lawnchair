package app.lawnchair.fold

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The [FoldDrawerLayout.EXPAND] drawer transition, "river": the first row goes straight to its new
 * place; the apps after it follow one after another ([FoldTunables.DRAWER_RIVER_STAGGER_MILLIS]
 * apart) along a small arc, the ones that were below the cover's bottom edge flowing up into the
 * new half. The header rows move with the first row ([prepareHeader]). After an untracked fold, on
 * the cover, the apps come back from the large positions in the [capture].
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DrawerRiverStyle(
    appsView: ActivityAllAppsContainerView<*>,
    dp: DeviceProfile,
    capture: FoldDrawerState.Capture?,
) : DrawerFoldStyle(appsView, dp, capture) {

    // The cover's grid position of each adapter position
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

    override fun prepare(scene: FoldScene, list: AllAppsRecyclerView) {
        arcPx = scene.dpToPx(FoldTunables.DRAWER_RIVER_ARC_DP)
        if (scene.largeLayout) {
            if (onLarge) prepareFromCover(scene, list)
        } else {
            prepareFromLarge(list)
        }
        prepareHeader(scene)
    }

    override fun onEnd(scene: FoldScene) {
        if (riverActive) {
            // Not only the attached children: a view recycled mid-transition (into the pool or
            // the view cache) is bound again without its translation being reset.
            for (i in riverMoved.indices) {
                val view = riverMoved[i]
                view.translationX = 0f
                view.translationY = 0f
            }
        }
        riverActive = false
        riverMoved.clear()
        super.onEnd(scene)
    }

    /** Large layout: each item comes from where the cover's drawer shows it. */
    private fun prepareFromCover(scene: FoldScene, list: AllAppsRecyclerView) {
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
    private fun prepareFromLarge(list: AllAppsRecyclerView) {
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
        val nominal = FoldTunables.DRAWER_RIVER_STAGGER_MILLIS / FoldTunables.UNFOLD_DURATION_MILLIS.toFloat()
        return if (movers <= 1) 0f else minOf(nominal, FoldTunables.DRAWER_MAX_DELAY / (movers - 1))
    }

    override fun apply(scene: FoldScene, list: AllAppsRecyclerView, t: Float) {
        if (!riverActive) return
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
                delay = (i * stagger).coerceAtMost(FoldTunables.DRAWER_MAX_DELAY)
            } else {
                if (gridPosition(list, child) == RecyclerView.NO_POSITION) continue
                val column = if (rtl) coverColumns - 1 - coverColumn[position] else coverColumn[position]
                fromX = toLarge(coverPaddingLeft + (column + 0.5f) * coverCellWidth)
                fromY = anchorCenterY + (coverRow[position] - coverRow[anchorPosition]) * coverCellHeight
                val rowOne = coverRow[position] <= coverRow[anchorPosition]
                delay = if (rowOne) 0f else ((coverOrdinal[position] - firstRiverOrdinal + 1) * stagger).coerceAtMost(FoldTunables.DRAWER_MAX_DELAY)
            }
            val local = ((t - delay) / (1f - FoldTunables.DRAWER_MAX_DELAY)).coerceIn(0f, 1f)
            val e = FoldTunables.DRAWER_EASE.getInterpolation(local)
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

    private fun layoutCenterY(scene: FoldScene, child: View): Float {
        locate(scene, child, center = true)
        return coords[1]
    }

    private companion object {
        /** Arcs of long paths bulge the full [FoldTunables.DRAWER_RIVER_ARC_DP]; short ones less. */
        const val ARC_REFERENCE = 2f
    }
}
