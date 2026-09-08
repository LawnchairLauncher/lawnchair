package app.lawnchair.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout

class ExpressiveFolderCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var cornerRadius: Float = 48f
        set(value) {
            field = value
            invalidate()
        }

    var cardBackgroundColor: Int = 0x80D1FAE5.toInt() // Semi-transparent mint/light green card background
        set(value) {
            field = value
            invalidate()
        }

    var isLargeFolder: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        bgPaint.color = cardBackgroundColor
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
    }
}
