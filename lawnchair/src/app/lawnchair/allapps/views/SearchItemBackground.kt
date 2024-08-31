package app.lawnchair.allapps.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R

class SearchItemBackground(
    context: Context,
    showBackground: Boolean,
    roundTop: Boolean,
    roundBottom: Boolean,
) {
    private val resources = context.resources

    private val searchDecorationPadding = resources.getDimensionPixelSize(R.dimen.search_decoration_padding)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpPath = Path()
    private val tmpRect = RectF()
    val focusHighlight = ColorTokens.FocusHighlight.resolveColor(context)
    val groupHighlight = if (showBackground) ColorTokens.GroupHighlight.resolveColor(context) else 0

    val cornerRadii: FloatArray

    init {
        val searchGroupRadius = resources.getDimensionPixelSize(R.dimen.search_group_radius).toFloat()
        val searchResultRadius = resources.getDimensionPixelSize(R.dimen.search_result_radius).toFloat()

        val topRadius = if (roundTop) searchGroupRadius else searchResultRadius
        val bottomRadius = if (roundBottom) searchGroupRadius else searchResultRadius

        cornerRadii = floatArrayOf(
            topRadius,
            topRadius,
            topRadius,
            topRadius,
            bottomRadius,
            bottomRadius,
            bottomRadius,
            bottomRadius,
        )
    }

    fun draw(c: Canvas, child: View, isFocused: Boolean) {
        val color = if (isFocused) focusHighlight else groupHighlight
        if (color == 0) return

        paint.color = color

        val left = child.left.toFloat() + searchDecorationPadding
        val top = child.top.toFloat() + searchDecorationPadding
        val right = child.right.toFloat() - searchDecorationPadding
        val bottom = child.bottom.toFloat() - searchDecorationPadding
        tmpRect.set(left, top, right, bottom)

        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, cornerRadii, Path.Direction.CW)

        c.drawPath(tmpPath, paint)
    }
}
