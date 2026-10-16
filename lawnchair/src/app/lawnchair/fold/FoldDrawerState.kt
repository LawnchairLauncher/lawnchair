package app.lawnchair.fold

import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.views.ActivityContext
import java.util.WeakHashMap

/**
 * What the app drawer looked like right before a DeviceProfile change on a foldable.
 *
 * The drawer container rebuilds every item view on a profile change (`swapAdapter` and a cleared
 * pool), which sends each list back to its top. Here each list's first visible item and its offset
 * are saved before that and restored after, so a fold or unfold keeps the drawer, the search
 * results and their scroll exactly where they were. The centers of the visible items and the
 * search bar's extent are kept too, for the drawer transitions ([DrawerFoldStyle]).
 *
 * Called by the drawer container (`ActivityAllAppsContainerView.onDeviceProfileChanged`) on
 * foldables only; phones and tablets are unchanged.
 */
object FoldDrawerState {

    private class Scroll(val position: Int, val offset: Int)

    private val scrolls = WeakHashMap<RecyclerView, Scroll>()
    private val anchors = WeakHashMap<RecyclerView, Int>()
    private val coords = FloatArray(2)

    /** Centers in root coordinates of the visible items of the list shown, by adapter position. */
    val itemCenters = SparseArray<FloatArray>()

    /** Left and right of the search bar's visible part in root coordinates, or NaN. */
    var searchBarLeft = Float.NaN
        private set
    var searchBarRight = Float.NaN
        private set

    /** Width of the window the capture was taken in. */
    var capturedWidth = 0
        private set

    /** [SystemClock.uptimeMillis] of the last capture. */
    var capturedAtUptimeMillis = 0L
        private set

    @JvmStatic
    fun beforeDeviceProfileChange(
        container: ActivityAllAppsContainerView<*>,
        main: RecyclerView?,
        work: RecyclerView?,
        search: RecyclerView?,
    ) {
        if (!isFoldable()) return
        saveScroll(main)
        saveScroll(work)
        saveScroll(search)
        capture(container)
    }

    @JvmStatic
    fun afterDeviceProfileChange(main: RecyclerView?, work: RecyclerView?, search: RecyclerView?) {
        if (!isFoldable()) return
        restoreScroll(main)
        restoreScroll(work)
        restoreScroll(search)
    }

    private fun isFoldable() = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

    private fun saveScroll(rv: RecyclerView?) {
        rv ?: return
        scrolls.remove(rv)
        anchors.remove(rv)
        if (!rv.isAttachedToWindow || rv.childCount == 0) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = rv.getChildAt(0)
        val position = rv.getChildAdapterPosition(first)
        if (position == RecyclerView.NO_POSITION) return
        scrolls[rv] = Scroll(position, lm.getDecoratedTop(first) - rv.paddingTop)
        anchors[rv] = position
    }

    /** The first visible adapter position of [rv] at the last capture, or [RecyclerView.NO_POSITION]. */
    fun anchorPositionOf(rv: RecyclerView): Int = anchors[rv] ?: RecyclerView.NO_POSITION

    /** Whether the last capture is recent enough to belong to a transition starting now. */
    fun isFresh(maxAgeMillis: Long = FRESH_MILLIS): Boolean = SystemClock.uptimeMillis() - capturedAtUptimeMillis <= maxAgeMillis

    private const val FRESH_MILLIS = 1000L

    private fun restoreScroll(rv: RecyclerView?) {
        rv ?: return
        val scroll = scrolls.remove(rv) ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val count = rv.adapter?.itemCount ?: 0
        if (scroll.position >= count) return
        lm.scrollToPositionWithOffset(scroll.position, scroll.offset)
    }

    private fun capture(container: ActivityAllAppsContainerView<*>) {
        itemCenters.clear()
        searchBarLeft = Float.NaN
        searchBarRight = Float.NaN
        val activity: ActivityContext = ActivityContext.lookupContext(container.context)
        val root = activity.dragLayer ?: return
        capturedWidth = root.width
        capturedAtUptimeMillis = SystemClock.uptimeMillis()
        val list = container.activeRecyclerView
        if (list != null && list.isAttachedToWindow) {
            for (i in 0 until list.childCount) {
                val child = list.getChildAt(i)
                val position = list.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) continue
                coords[0] = child.width / 2f
                coords[1] = child.height / 2f
                Utilities.getDescendantCoordRelativeToAncestor(child, root, coords, false)
                itemCenters.put(position, floatArrayOf(coords[0], coords[1]))
            }
        }
        val bar: View? = container.searchView
        if (bar != null && bar.isAttachedToWindow && bar.width > 0) {
            coords[0] = 0f
            coords[1] = 0f
            Utilities.getDescendantCoordRelativeToAncestor(bar, root, coords, false)
            searchBarLeft = coords[0] + bar.paddingLeft
            searchBarRight = coords[0] + bar.width - bar.paddingRight
        }
    }
}
