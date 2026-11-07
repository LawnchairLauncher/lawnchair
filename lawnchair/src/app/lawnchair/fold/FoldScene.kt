package app.lawnchair.fold

import android.graphics.Matrix
import android.graphics.Rect
import android.view.Gravity
import android.view.Surface
import android.view.View
import com.android.launcher3.CellLayout
import com.android.launcher3.LauncherRootView
import com.android.launcher3.Workspace
import com.android.launcher3.dragndrop.DragLayer

/**
 * What a [FoldStyle] needs to know about the transition being played, and the launcher views it
 * may draw or move.
 *
 * @property opening the direction the transition started in.
 * @property largeLayout true when the transition is shown on the unfolded (two-half) layout: the
 * moving half is visible and progress 1 is the rest state. False on the cover layout after a fold,
 * where progress runs from 1 to 0 and 0 is the rest state.
 */
class FoldScene internal constructor(
    val rootView: LauncherRootView,
    val dragLayer: DragLayer,
    private val workspace: Workspace<*>?,
    private val density: Float,
    val opening: Boolean,
    val largeLayout: Boolean,
) {
    val geometry = FoldGeometry()

    fun dpToPx(dp: Float): Float = dp * density

    /** Sets [out] to the workspace pages on screen, none while the workspace is hidden. */
    fun getVisiblePages(out: ArrayList<CellLayout>) {
        out.clear()
        val workspace = workspace ?: return
        if (workspace.visibility != View.VISIBLE) return
        // The range array is shared by the workspace: read it at once
        val range = workspace.visibleChildrenRange
        val first = range[0]
        val last = range[1]
        if (first < 0) return
        for (i in first..last) {
            val page = workspace.getChildAt(i) as? CellLayout ?: continue
            if (page.visibility == View.VISIBLE) out.add(page)
        }
    }
}

/**
 * The two halves of the window, in [LauncherRootView] coordinates.
 *
 * The hinge is assumed to be at the middle of the window. Book-style foldables (Pixel Fold,
 * Galaxy Z Fold, the 7.6" emulator) have the hinge on the left of the cover screen, so in the
 * natural orientation the newly revealed (moving) half is the left one; rotation moves it.
 * [movingEdgeFor] reads the side from [FoldPosture].
 *
 * Styles work in "hinge space": `u` is the distance from the hinge toward the free edge
 * (0..[halfSpan]) and `v` runs along the hinge (0..[hingeLength]). [toRootX]/[toRootY] map it back.
 */
class FoldGeometry {
    val movingRect = Rect()
    val fixedRect = Rect()

    /** [Gravity.LEFT], [Gravity.TOP], [Gravity.RIGHT] or [Gravity.BOTTOM]: where the moving half is. */
    var movingEdge = Gravity.LEFT
        private set
    var width = 0
        private set
    var height = 0
        private set

    /** Position of the hinge line along the axis across it. */
    var hingePosition = 0f
        private set

    /** Distance from the hinge to the free edge of the moving half. */
    var halfSpan = 0f
        private set

    /** Length of the hinge (the extent of the moving half along it). */
    var hingeLength = 0f
        private set

    /** Incremented on every change, so styles can rebuild size-dependent objects only when needed. */
    var version = 0
        private set

    val isValid: Boolean get() = width > 0 && height > 0
    val isVerticalHinge: Boolean get() = movingEdge == Gravity.LEFT || movingEdge == Gravity.RIGHT

    /** Unit vector pointing from the moving half toward the hinge. */
    val towardHingeX: Float get() = when (movingEdge) {
        Gravity.LEFT -> 1f
        Gravity.RIGHT -> -1f
        else -> 0f
    }
    val towardHingeY: Float get() = when (movingEdge) {
        Gravity.TOP -> 1f
        Gravity.BOTTOM -> -1f
        else -> 0f
    }

    /** The middle of the screen edge across from the moving half: the fixed half's outer edge, the cover's on the cover. */
    val outerEdgeX: Float get() = when (movingEdge) {
        Gravity.LEFT -> width.toFloat()
        Gravity.RIGHT -> 0f
        else -> width / 2f
    }
    val outerEdgeY: Float get() = when (movingEdge) {
        Gravity.TOP -> height.toFloat()
        Gravity.BOTTOM -> 0f
        else -> height / 2f
    }

    /** Returns true if anything changed. */
    fun update(width: Int, height: Int, movingEdge: Int): Boolean {
        if (width == this.width && height == this.height && movingEdge == this.movingEdge) return false
        this.width = width
        this.height = height
        this.movingEdge = movingEdge
        when (movingEdge) {
            Gravity.LEFT, Gravity.RIGHT -> {
                val hinge = width / 2
                hingePosition = hinge.toFloat()
                hingeLength = height.toFloat()
                if (movingEdge == Gravity.LEFT) {
                    movingRect.set(0, 0, hinge, height)
                    fixedRect.set(hinge, 0, width, height)
                } else {
                    movingRect.set(hinge, 0, width, height)
                    fixedRect.set(0, 0, hinge, height)
                }
                halfSpan = movingRect.width().toFloat()
            }

            else -> {
                val hinge = height / 2
                hingePosition = hinge.toFloat()
                hingeLength = width.toFloat()
                if (movingEdge == Gravity.TOP) {
                    movingRect.set(0, 0, width, hinge)
                    fixedRect.set(0, hinge, width, height)
                } else {
                    movingRect.set(0, hinge, width, height)
                    fixedRect.set(0, 0, width, hinge)
                }
                halfSpan = movingRect.height().toFloat()
            }
        }
        version++
        return true
    }

    fun toRootX(u: Float, v: Float): Float = when (movingEdge) {
        Gravity.LEFT -> hingePosition - u
        Gravity.RIGHT -> hingePosition + u
        else -> v
    }

    fun toRootY(u: Float, v: Float): Float = when (movingEdge) {
        Gravity.TOP -> hingePosition - u
        Gravity.BOTTOM -> hingePosition + u
        else -> v
    }

    /** Extent across the hinge that a style sweeps: the moving half, or the whole cover screen. */
    fun sweepSpan(largeLayout: Boolean): Float = when {
        largeLayout -> halfSpan
        isVerticalHinge -> width.toFloat()
        else -> height.toFloat()
    }

    /**
     * Sets [out] to map hinge space to root coordinates. On the large layout that is [toRootX] /
     * [toRootY]; on the cover screen `u` starts at the screen edge where the hinge is and points
     * into the screen.
     */
    fun getHingeFrame(out: Matrix, largeLayout: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val origin = if (largeLayout) hingePosition else 0f
        when (movingEdge) {
            Gravity.LEFT -> if (largeLayout) {
                out.setScale(-1f, 1f)
                out.postTranslate(origin, 0f)
            } else {
                out.reset()
            }

            Gravity.RIGHT -> if (largeLayout) {
                out.setTranslate(origin, 0f)
            } else {
                out.setScale(-1f, 1f)
                out.postTranslate(w, 0f)
            }

            // x = v; y runs along u
            Gravity.TOP -> setFrame(out, if (largeLayout) -1f else 1f, if (largeLayout) origin else 0f)

            else -> setFrame(out, if (largeLayout) 1f else -1f, if (largeLayout) origin else h)
        }
    }

    private val frameValues = FloatArray(9)

    private fun setFrame(out: Matrix, uSign: Float, yOrigin: Float) {
        frameValues[Matrix.MSCALE_X] = 0f
        frameValues[Matrix.MSKEW_X] = 1f
        frameValues[Matrix.MTRANS_X] = 0f
        frameValues[Matrix.MSKEW_Y] = uSign
        frameValues[Matrix.MSCALE_Y] = 0f
        frameValues[Matrix.MTRANS_Y] = yOrigin
        frameValues[Matrix.MPERSP_0] = 0f
        frameValues[Matrix.MPERSP_1] = 0f
        frameValues[Matrix.MPERSP_2] = 1f
        out.setValues(frameValues)
    }

    companion object {
        /**
         * The moving half for a display rotation. [Surface.ROTATION_90] means the device was turned
         * counter-clockwise, so the natural left edge is now at the bottom.
         */
        fun movingEdgeFor(rotation: Int): Int = when (FoldPosture.isMovingHalfLeft(rotation)) {
            true -> Gravity.LEFT

            false -> Gravity.RIGHT

            // Horizontal fold line: fall back to the rotation of a book-style hinge
            null -> when (rotation) {
                Surface.ROTATION_90 -> Gravity.BOTTOM
                Surface.ROTATION_180 -> Gravity.RIGHT
                Surface.ROTATION_270 -> Gravity.TOP
                else -> Gravity.LEFT
            }
        }
    }
}
