package app.lawnchair.fold

import android.graphics.Rect
import android.view.Gravity
import android.view.Surface
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherRootView
import com.android.launcher3.dragndrop.DragLayer

/**
 * What a [FoldStyle] needs to know about the transition being played.
 *
 * @property opening the direction the transition started in.
 * @property largeLayout true when the transition is shown on the unfolded (two-half) layout: the
 * moving half is visible and progress 1 is the rest state. False on the cover layout after a fold,
 * where progress runs from 1 to 0 and 0 is the rest state.
 * @property reducedMotion the system animator duration scale is 0; styles must not move anything.
 */
class FoldScene internal constructor(
    val launcher: LawnchairLauncher,
    val opening: Boolean,
    val largeLayout: Boolean,
    val reducedMotion: Boolean,
) {
    val geometry = FoldGeometry()

    val rootView: LauncherRootView get() = launcher.rootView
    val dragLayer: DragLayer get() = launcher.dragLayer

    fun dpToPx(dp: Float): Float = dp * launcher.resources.displayMetrics.density
}

/**
 * The two halves of the window, in [LauncherRootView] coordinates.
 *
 * The hinge is assumed to be at the middle of the window. Book-style foldables (Pixel Fold,
 * Galaxy Z Fold, the 7.6" emulator) have the hinge on the left of the cover screen, so in the
 * natural orientation the newly revealed (moving) half is the left one; rotation moves it. Brief C
 * adds `FoldPosture.hingeSide`; once merged, [movingEdgeFor] should read the side from there.
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

    companion object {
        /**
         * The moving half for a display rotation. [Surface.ROTATION_90] means the device was turned
         * counter-clockwise, so the natural left edge is now at the bottom.
         */
        fun movingEdgeFor(rotation: Int): Int = when (FoldPosture.getHingeSide(rotation, assumeBookStyle = true)) {
            HingeSide.LEFT -> Gravity.LEFT
            HingeSide.RIGHT -> Gravity.RIGHT
            // Horizontal fold line (or no hinge seen yet): fall back to the rotation of a book-style hinge
            HingeSide.UNKNOWN -> when (rotation) {
                Surface.ROTATION_90 -> Gravity.BOTTOM
                Surface.ROTATION_180 -> Gravity.RIGHT
                Surface.ROTATION_270 -> Gravity.TOP
                else -> Gravity.LEFT
            }
        }
    }
}
