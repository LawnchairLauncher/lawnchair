package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

open class StretchRecyclerViewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : StretchRelativeLayout(context, attrs, defStyleAttr) {

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        return super.drawChild(canvas, child, drawingTime)
    }

    open fun clipChild(canvas: Canvas, child: View) {
        canvas.clipRect(child.left, child.top, child.right, child.bottom)
    }
}
