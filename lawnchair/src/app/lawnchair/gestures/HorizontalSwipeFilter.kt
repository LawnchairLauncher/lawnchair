package app.lawnchair.gestures

import kotlin.math.abs
import kotlin.math.max

/**
 * Decides whether a touch stream that started on a paged dock is a horizontal paging swipe.
 *
 * Thumb swipes are rarely perfectly horizontal; the vertical swipe gesture detector has no angle
 * check, so without this a small vertical drift steals the touch from the dock pager.
 */
object HorizontalSwipeFilter {

    /** The swipe is horizontal while |dy| stays below this multiple of |dx| (about 63 degrees). */
    const val VERTICAL_DOMINANCE_RATIO = 2f

    /**
     * @param dx horizontal displacement since the touch went down
     * @param dy vertical displacement since the touch went down
     * @param minDistance displacement below which the direction is still undecided
     */
    @JvmStatic
    fun isHorizontalSwipe(dx: Float, dy: Float, minDistance: Float): Boolean {
        if (max(abs(dx), abs(dy)) < minDistance) return false
        return abs(dy) < abs(dx) * VERTICAL_DOMINANCE_RATIO
    }
}
