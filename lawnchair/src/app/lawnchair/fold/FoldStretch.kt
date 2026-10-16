package app.lawnchair.fold

import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stretches the content of [view] (the drawer search bar, the personal/work tabs) between two
 * widths without a layout request, so a transition does not lay the whole window out every frame.
 *
 * [capture] keeps the children's frames of the current layout, which is the final one; [apply]
 * maps them linearly onto another content box and measures and lays out only those children (and
 * so only their subtrees), leaving [view]'s padding as it is. If a real layout runs meanwhile, the
 * next [apply] puts the children back. [restore] asks for one normal layout at the end.
 */
internal class FoldStretch(private val view: ViewGroup) {

    private var lefts = IntArray(0)
    private var rights = IntArray(0)
    private var contentLeft = 0
    private var contentRight = 0

    /** Keeps the current layout. Returns false when there is nothing to stretch. */
    fun capture(): Boolean {
        val count = view.childCount
        contentLeft = view.paddingLeft
        contentRight = view.width - view.paddingRight
        if (count == 0 || contentRight <= contentLeft) return false
        lefts = IntArray(count) { view.getChildAt(it).left }
        rights = IntArray(count) { view.getChildAt(it).right }
        return true
    }

    /** Lays the children out in the content box [left]..[right], in [view]'s coordinates. */
    fun apply(left: Int, right: Int) {
        val scale = (right - left).coerceAtLeast(0).toFloat() / (contentRight - contentLeft)
        for (i in 0 until min(view.childCount, lefts.size)) {
            val child = view.getChildAt(i)
            if (child.visibility == View.GONE) continue
            val childLeft = left + ((lefts[i] - contentLeft) * scale).roundToInt()
            val childRight = left + ((rights[i] - contentLeft) * scale).roundToInt()
            if (child.left == childLeft && child.right == childRight) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(childRight - childLeft, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(child.height, MeasureSpec.EXACTLY),
            )
            child.layout(childLeft, child.top, childRight, child.bottom)
        }
    }

    /** Back to the normal layout. */
    fun restore() {
        view.requestLayout()
    }
}
