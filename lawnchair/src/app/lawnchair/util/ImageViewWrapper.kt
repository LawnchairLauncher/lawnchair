package app.lawnchair.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.android.systemui.shared.system.BlurUtils

class ImageViewWrapper(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {

    private val mRadius = resources.getDimensionPixelSize(R.dimen.search_row_preview_radius).toFloat()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    val supportBlur = BlurUtils.supportsBlursOnWindows()
    val groupHighlight = if (supportBlur) {
        ColorTokens.GroupHighlightBlur.resolveColor(context)
    } else {
        ColorTokens.GroupHighlight.resolveColor(context)
    }

    init {
        scaleType = ScaleType.CENTER_CROP
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val corners = floatArrayOf(
            mRadius,
            mRadius,
            mRadius,
            mRadius,
            mRadius,
            mRadius,
            mRadius,
            mRadius,
        )
        path.addRoundRect(rect, corners, Path.Direction.CW)

        paint.color = groupHighlight
        canvas.clipPath(path)
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }
}
