package app.lawnchair.fold

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * SIMPLE: what was on the cover stays exactly where it is; the newly revealed half fades in while
 * sliding [FoldTunables.SIMPLE_TRANSLATE_DP] out of the hinge.
 *
 * With [fadeOnly] it is a plain crossfade, used for reduced motion. Nothing moves on the cover
 * layout after a fold: the cover content was on the fixed half already.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SimpleFoldStyle(
    private val fadeOnly: Boolean = false,
) : SplitFoldStyle() {

    private var translatePx = 0f

    override fun onStart(scene: FoldScene): Boolean {
        translatePx = if (fadeOnly || scene.reducedMotion) 0f else scene.dpToPx(FoldTunables.SIMPLE_TRANSLATE_DP)
        movingNode.setHasOverlappingRendering(true)
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        if (!scene.largeLayout) return
        clipDragLayerToFixedHalf(scene)
        val p = progress.coerceIn(0f, 1f)
        val offset = (1f - p) * translatePx
        movingNode.setAlpha(p)
        movingNode.setTranslationX(scene.geometry.towardHingeX * offset)
        movingNode.setTranslationY(scene.geometry.towardHingeY * offset)
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
