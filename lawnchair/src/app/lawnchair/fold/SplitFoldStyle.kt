package app.lawnchair.fold

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Base for styles that treat the two halves differently.
 *
 * The live launcher (the DragLayer) is clipped to the fixed half, so nothing on it moves. The moving
 * half is drawn by the style, in the overlay, from [movingNode]: a RenderNode that records the
 * DragLayer every frame and only references its children's display lists. So the moving half is
 * always the live content (icons bound late, widgets updating) with the style's transform on top,
 * and nothing is copied into a bitmap. The fixed half can take part through [transformDragLayer]
 * and [fadeDragLayer].
 */
@RequiresApi(Build.VERSION_CODES.Q)
abstract class SplitFoldStyle : FoldStyle() {

    protected val movingNode = RenderNode("FoldMovingHalf")

    private val dragLayerClip = Rect()
    private val dragLayerMatrix = Matrix()
    private var savedDragLayerClip: Rect? = null
    private var dragLayerClipped = false

    override fun usesOverlay(scene: FoldScene): Boolean = scene.largeLayout

    /** Hides the moving half of the live views; [recordMovingHalf] draws it instead. */
    protected fun clipDragLayerToFixedHalf(scene: FoldScene) {
        val dragLayer = scene.dragLayer
        if (!dragLayerClipped) {
            savedDragLayerClip = dragLayer.clipBounds
            dragLayerClipped = true
            dragLayerClip.setEmpty()
        }
        val fixed = scene.geometry.fixedRect
        val left = fixed.left - dragLayer.left
        val top = fixed.top - dragLayer.top
        val right = fixed.right - dragLayer.left
        val bottom = fixed.bottom - dragLayer.top
        if (dragLayerClip.left != left || dragLayerClip.top != top || dragLayerClip.right != right || dragLayerClip.bottom != bottom) {
            dragLayerClip.set(left, top, right, bottom)
            dragLayer.clipBounds = dragLayerClip
        }
    }

    /** Records the moving half of the live views into [movingNode], placed at the moving half. */
    protected fun recordMovingHalf(scene: FoldScene) {
        val rect = scene.geometry.movingRect
        val dragLayer = scene.dragLayer
        movingNode.setPosition(rect)
        val canvas = movingNode.beginRecording(rect.width(), rect.height())
        try {
            canvas.translate((dragLayer.left - rect.left).toFloat(), (dragLayer.top - rect.top).toFloat())
            dragLayer.draw(canvas)
        } finally {
            movingNode.endRecording()
        }
    }

    /**
     * Draws the live views through [matrix], in root coordinates, on top of their own transforms.
     * It is the DragLayer's animation matrix: drawing only, touch is not affected.
     */
    protected fun transformDragLayer(scene: FoldScene, matrix: Matrix) {
        val dragLayer = scene.dragLayer
        dragLayerMatrix.set(matrix)
        dragLayerMatrix.preTranslate(dragLayer.left.toFloat(), dragLayer.top.toFloat())
        dragLayerMatrix.postTranslate(-dragLayer.left.toFloat(), -dragLayer.top.toFloat())
        dragLayer.animationMatrix = dragLayerMatrix
    }

    /** Fades the live views to [alpha], apart from their own alpha (the DragLayer's transition alpha). */
    protected fun fadeDragLayer(scene: FoldScene, alpha: Float) {
        scene.dragLayer.transitionAlpha = alpha
    }

    override fun onEnd(scene: FoldScene) {
        val dragLayer = scene.dragLayer
        if (dragLayerClipped) {
            dragLayerClipped = false
            dragLayer.clipBounds = savedDragLayerClip
            savedDragLayerClip = null
        }
        dragLayer.animationMatrix = null
        dragLayer.transitionAlpha = 1f
        movingNode.discardDisplayList()
    }
}
