package app.lawnchair.fold

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * SIMPLE: a short fade of the new layout over the whole screen. The newly revealed half fades in
 * where it is, and the page you were on dips to [FoldTunables.SIMPLE_FIXED_ALPHA] and comes back
 * with it. Nothing moves, so it is also what every style plays with reduced motion. On the cover
 * layout after a fold, the cover page comes back from the same dip.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SimpleFoldStyle : SplitFoldStyle() {

    override fun onStart(scene: FoldScene): Boolean {
        movingNode.setHasOverlappingRendering(true)
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        // How far from the rest state: 1 - progress on the large layout, progress on the cover
        val dip = if (scene.largeLayout) 1f - p else p
        fadeDragLayer(scene, 1f - (1f - FoldTunables.SIMPLE_FIXED_ALPHA) * dip)
        if (!scene.largeLayout) return
        clipDragLayerToFixedHalf(scene)
        movingNode.setAlpha(p)
    }

    override fun onDrawOverlay(scene: FoldScene, canvas: Canvas, progress: Float) {
        if (!scene.largeLayout || !scene.geometry.isValid || progress <= 0f) return
        recordMovingHalf(scene)
        val save = canvas.save()
        canvas.clipRect(scene.geometry.movingRect)
        canvas.drawRenderNode(movingNode)
        canvas.restoreToCount(save)
    }
}
