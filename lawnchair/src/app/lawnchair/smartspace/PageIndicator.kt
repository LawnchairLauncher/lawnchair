package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import kotlin.math.roundToInt

class PageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    private var primaryColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private var currentPageIndex = -1
    private var positionOffset = 0f
    private var numPages = -1
    private var dotRadius = 0f
    private var diameter = 0f
    private var gapWidth = 0f

    fun setNumPages(numPages: Int) {
        if (this.numPages != numPages) {
            this.numPages = numPages
            currentPageIndex = if (numPages > 0) currentPageIndex.coerceIn(0 until numPages) else -1
            requestLayout()
            invalidate()
        }
        isVisible = numPages >= 2
    }

    fun setPageOffset(position: Int, positionOffset: Float) {
        val clampedPosition = if (numPages > 0) position.coerceIn(0, numPages - 1) else position
        val clampedOffset = positionOffset.coerceIn(0f, 1f)

        if (this.currentPageIndex == clampedPosition && this.positionOffset == clampedOffset) return
        this.currentPageIndex = clampedPosition
        this.positionOffset = clampedOffset

        if (numPages > 0) {
            val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val rawActivePos = if (clampedOffset < 0.5f) clampedPosition else clampedPosition + 1
            val activePos = rawActivePos.coerceIn(0, numPages - 1)
            val displayPage = if (isRtl) numPages - activePos else activePos + 1
            contentDescription = context.getString(
                R.string.accessibility_smartspace_page,
                displayPage,
                numPages,
            )
        }
        invalidate()
    }

    private fun updateDotMetrics() {
        dotRadius = resources.getDimension(R.dimen.page_indicator_dot_size) / 2f
        diameter = 2f * dotRadius
        gapWidth = resources.getDimension(R.dimen.page_indicator_gap_width)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (numPages < 2) {
            setMeasuredDimension(0, 0)
            return
        }
        updateDotMetrics()

        val contentWidth = (numPages + 1) * diameter + (numPages - 1) * gapWidth
        val contentHeight = diameter

        val width = resolveSize((contentWidth + paddingLeft + paddingRight).toInt(), widthMeasureSpec)
        val height = resolveSize((contentHeight + paddingTop + paddingBottom).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (numPages < 2) return

        val contentWidth = (numPages + 1) * diameter + (numPages - 1) * gapWidth
        val contentHeight = diameter

        val startX = paddingLeft + (width - paddingLeft - paddingRight - contentWidth) / 2f
        val startY = paddingTop + (height - paddingTop - paddingBottom - contentHeight) / 2f

        val pos = currentPageIndex.coerceIn(0, numPages - 1)
        val offset = positionOffset.coerceIn(0f, 1f)

        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val activeIndex = if (isRtl) numPages - 1 - pos else pos
        val nextActiveIndex = if (isRtl) activeIndex - 1 else activeIndex + 1

        paint.color = primaryColor
        val baseAlpha = Color.alpha(primaryColor)

        var currentX = startX
        for (i in 0 until numPages) {
            val activeFraction = when (i) {
                activeIndex -> 1f - offset
                nextActiveIndex -> offset
                else -> 0f
            }

            val alphaFraction = 0.5f + 0.5f * activeFraction
            val dotWidth = diameter * (1f + activeFraction)

            rect.set(
                currentX,
                startY,
                currentX + dotWidth,
                startY + diameter,
            )
            paint.alpha = (baseAlpha * alphaFraction).roundToInt()

            canvas.drawRoundRect(rect, dotRadius, dotRadius, paint)

            currentX += dotWidth + gapWidth
        }
    }
}
