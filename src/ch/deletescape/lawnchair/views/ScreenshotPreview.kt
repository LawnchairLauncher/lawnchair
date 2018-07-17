package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.backup_item.view.*

class ScreenshotPreview(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.measureChild(preview, widthMeasureSpec, heightMeasureSpec)
        val measuredWidth = preview.measuredWidth
        val measuredHeight = preview.measuredHeight
        super.measureChild(wallpaper, MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}
