package ch.deletescape.lawnchair.views

import android.content.Context
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.forEachChild
import kotlin.math.max

class WrapContentHeightViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {

    var childFilter: (View) -> Boolean = { true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            var height = 0
            forEachChild {
                it.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (childFilter(it)) {
                    height = max(height, it.measuredHeight)
                }
            }

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
