package app.lawnchair.fold

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.android.launcher3.DeviceProfile
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.util.ScrollableLayoutManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The [FoldDrawerLayout.BIG_ICONS] drawer transition, "zoom": the grid and the header rows scale
 * from the cover's size up to the full width, anchored at the outer edge of the fixed half, so they
 * grow across the hinge. After an untracked fold, on the cover, they come down from the large size.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DrawerZoomStyle(
    appsView: ActivityAllAppsContainerView<*>,
    dp: DeviceProfile,
    capture: FoldDrawerState.Capture?,
) : DrawerFoldStyle(appsView, dp, capture) {

    private var zoomFrom = 1f
    private var zoomActive = false
    private var pivotRootX = 0f
    private var pivotRootY = 0f
    private var savedClipChildren = true

    override fun onStart(scene: FoldScene): Boolean {
        if (!super.onStart(scene)) return false
        // More rows laid out below before the first frame, so its first, smallest frame is full
        val cover = cover
        val large = large
        if (!searching && scene.largeLayout && onLarge && cover != null && large != null) {
            zoomFrom = zoomScale(cover, large)
            val height = large.deviceProperties.heightPx
            (list?.layoutManager as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(
                (height * (1f / zoomFrom - 1f)).roundToInt().coerceAtLeast(0),
            )
        }
        return true
    }

    override fun prepare(scene: FoldScene, list: AllAppsRecyclerView) {
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
        locate(scene, header ?: list)
        pivotRootX = outerEdge
        pivotRootY = coords[1]
        savedClipChildren = list.clipChildren
        list.clipChildren = false
        zoomActive = true
    }

    override fun apply(scene: FoldScene, list: AllAppsRecyclerView, t: Float) {
        if (!zoomActive) return
        val scale = zoomFrom + (1f - zoomFrom) * FoldTunables.DRAWER_EASE.getInterpolation(t)
        zoomView(scene, list, scale)
        zoomView(scene, header, scale)
    }

    override fun onEnd(scene: FoldScene) {
        val list = list
        if (list != null) {
            if (zoomActive) {
                resetZoom(list)
                resetZoom(header)
                list.clipChildren = savedClipChildren
            }
            (list.layoutManager as? ScrollableLayoutManager)?.setFoldExtraLayoutSpace(0)
        }
        zoomActive = false
        super.onEnd(scene)
    }

    private fun zoomView(scene: FoldScene, view: View?, scale: Float) {
        view ?: return
        locate(scene, view)
        val pivotX = pivotRootX - coords[0]
        val pivotY = pivotRootY - coords[1]
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
}
