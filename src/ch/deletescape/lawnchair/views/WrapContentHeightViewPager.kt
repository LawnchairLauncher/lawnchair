package ch.deletescape.lawnchair.views

import android.content.Context
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import ch.deletescape.lawnchair.forEachChild
import kotlin.math.max

class WrapContentHeightViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var height = 0
        forEachChild {
            it.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            height = max(height, it.measuredHeight)
        }

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
