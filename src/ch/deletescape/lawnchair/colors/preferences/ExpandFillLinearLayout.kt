package ch.deletescape.lawnchair.colors.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import ch.deletescape.lawnchair.forEachChildIndexed

class ExpandFillLinearLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    var childWidth = 0
    var childHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // TODO: why this works perfectly with plain views but nothing else
        val fillLayout = if (orientation == HORIZONTAL) {
            val exactHeight = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
            performMeasure(widthMeasureSpec, childWidth) { view, spec ->
                measureChild(view, spec, exactHeight)
            }
        } else {
            val exactWidth = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY)
            performMeasure(heightMeasureSpec, childHeight) { view, spec ->
                measureChild(view, exactWidth, spec)
            }
        }
        if (fillLayout) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private inline fun performMeasure(spec: Int, childSize: Int, crossinline measureChild: (View, Int) -> Unit): Boolean {
        val available = MeasureSpec.getSize(spec)
        if (childSize * childCount >= available) return false
        val width = available / childCount
        val used = width * childCount
        val remaining = available - used
        forEachChildIndexed { view, i ->
            if (i < remaining) measureChild(view, MeasureSpec.makeMeasureSpec(width + 1, MeasureSpec.EXACTLY))
            else measureChild(view, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY))
        }
        return true
    }
}
