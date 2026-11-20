package app.lawnchair.fold

import android.util.SparseArray
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import java.util.WeakHashMap

/**
 * The app drawer across a DeviceProfile change on a foldable.
 *
 * The drawer container rebuilds every item view on a profile change (`swapAdapter` and a cleared
 * pool), which sends each list back to its top. Here each list's first visible item and its offset
 * are saved before that and restored after, so a fold or unfold keeps the drawer, the search
 * results and their scroll exactly where they were. The book drawer ([FoldDrawerBook]) hears of the
 * change from here too. What the drawer showed ([Capture]) is taken by [capture] before the change
 * is dispatched and goes to the fold transition engine, which hands it to [DrawerFoldStyle].
 *
 * Called by the drawer container (`ActivityAllAppsContainerView.onDeviceProfileChanged`) and by
 * [LawnchairLauncher], on foldables only; phones and tablets are unchanged.
 */
object FoldDrawerState {

    private class Scroll(val position: Int, val offset: Int)

    /**
     * What the drawer showed right before a DeviceProfile change, in the window of [width]:
     * [anchorPosition] is the first visible adapter position of the list shown (or
     * [RecyclerView.NO_POSITION]), [itemCenters] the centers in root coordinates of its visible
     * items by adapter position, and [searchBarLeft]..[searchBarRight] the search bar's visible
     * part in root coordinates (NaN without a bar).
     */
    class Capture(
        val width: Int,
        val anchorPosition: Int,
        val itemCenters: SparseArray<FloatArray>,
        val searchBarLeft: Float,
        val searchBarRight: Float,
    )

    private val scrolls = WeakHashMap<RecyclerView, Scroll>()
    private val coords = FloatArray(2)

    @JvmStatic
    fun beforeDeviceProfileChange(main: RecyclerView?, work: RecyclerView?, search: RecyclerView?) {
        if (!isFoldable()) return
        saveScroll(main)
        saveScroll(work)
        saveScroll(search)
    }

    @JvmStatic
    fun afterDeviceProfileChange(main: RecyclerView?, work: RecyclerView?, search: RecyclerView?) {
        if (!isFoldable()) return
        restoreScroll(main)
        restoreScroll(work)
        restoreScroll(search)
        FoldDrawerBook.of(main)?.onDeviceProfileChanged()
        FoldDrawerBook.of(work)?.onDeviceProfileChanged()
    }

    private fun isFoldable() = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

    private fun saveScroll(rv: RecyclerView?) {
        rv ?: return
        scrolls.remove(rv)
        if (!rv.isAttachedToWindow || rv.childCount == 0) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = rv.getChildAt(0)
        val position = rv.getChildAdapterPosition(first)
        if (position == RecyclerView.NO_POSITION) return
        scrolls[rv] = Scroll(position, lm.getDecoratedTop(first) - rv.paddingTop)
    }

    private fun restoreScroll(rv: RecyclerView?) {
        rv ?: return
        val scroll = scrolls.remove(rv) ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val count = rv.adapter?.itemCount ?: 0
        if (scroll.position >= count) return
        lm.scrollToPositionWithOffset(scroll.position, scroll.offset)
    }

    /**
     * Hands what the drawer shows to the launcher's fold transition engine. [LawnchairLauncher]
     * calls this before it dispatches a profile change, so before any listener hears of it and
     * before any view is laid out again.
     */
    fun capture(launcher: LawnchairLauncher): Capture? {
        if (!isFoldable()) return null
        val container = launcher.appsView ?: return null
        val root = launcher.dragLayer ?: return null
        val centers = SparseArray<FloatArray>()
        var anchor = RecyclerView.NO_POSITION
        val list = container.activeRecyclerView
        if (list != null && list.isAttachedToWindow) {
            for (i in 0 until list.childCount) {
                val child = list.getChildAt(i)
                val position = list.getChildAdapterPosition(child)
                if (i == 0) anchor = position
                if (position == RecyclerView.NO_POSITION) continue
                coords[0] = child.width / 2f
                coords[1] = child.height / 2f
                Utilities.getDescendantCoordRelativeToAncestor(child, root, coords, false)
                centers.put(position, floatArrayOf(coords[0], coords[1]))
            }
        }
        var barLeft = Float.NaN
        var barRight = Float.NaN
        val bar: View? = container.searchView
        if (bar != null && bar.isAttachedToWindow && bar.width > 0) {
            coords[0] = 0f
            coords[1] = 0f
            Utilities.getDescendantCoordRelativeToAncestor(bar, root, coords, false)
            barLeft = coords[0] + bar.paddingLeft
            barRight = coords[0] + bar.width - bar.paddingRight
        }
        return Capture(root.width, anchor, centers, barLeft, barRight)
    }
}
