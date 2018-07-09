package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class PreviewFrame(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.drawPaint(clearPaint)

        super.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return false // don't accept any touch here
    }
}
