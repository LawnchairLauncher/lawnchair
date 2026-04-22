package app.lawnchair.smartspace

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.android.launcher3.R
import com.android.launcher3.util.Themes

class PageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val primaryColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private var currentPageIndex = -1
    private var numPages = -1

    fun setNumPages(numPages: Int) {
        if (numPages <= 0) {
            setNumPages(1)
            return
        }
        if (numPages < 2) {
            isVisible = false
            return
        }
        isVisible = true
        if (this.numPages != numPages) {
            this.numPages = numPages
            initializePageIndicators()
        }
    }

    fun setPageOffset(position: Int, positionOffset: Float) {
        val zeroOffset = positionOffset == 0f
        if (zeroOffset && position == currentPageIndex) return
        if (position !in 0 until childCount) return

        val currentDot = getChildAt(position) as? ImageView
        val nextDot = getChildAt(position + 1) as? ImageView
        if (currentDot == null || nextDot == null) return
        currentDot.alpha = (1f - positionOffset) * 0.6f + 0.4f
        nextDot.alpha = 0.6f * positionOffset + 0.4f
        contentDescription = context.getString(
            R.string.accessibility_smartspace_page,
            if (positionOffset.toDouble() < 0.5) position + 1 else position + 2,
            numPages,
        )
        if (zeroOffset) {
            currentPageIndex = position
        } else if (positionOffset >= 0.99f) {
            currentPageIndex = position + 1
        }
    }

    private fun initializePageIndicators() {
        val childCount = childCount
        for (i in 0 until childCount - numPages) {
            removeViewAt(0)
        }
        val dotMargin =
            context.resources.getDimensionPixelSize(R.dimen.page_indicator_dot_margin)
        currentPageIndex = currentPageIndex.coerceIn(0 until numPages)
        for (i in 0 until numPages) {
            val reuse = i < getChildCount()
            val imageView = if (reuse) getChildAt(i) as ImageView else ImageView(context)
            val layoutParams = if (reuse) {
                imageView.layoutParams as LayoutParams
            } else {
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            if (i == 0) {
                layoutParams.marginStart = 0
            } else {
                layoutParams.marginStart = dotMargin
            }
            if (i == numPages - 1) {
                layoutParams.marginEnd = 0
            } else {
                layoutParams.marginEnd = dotMargin
            }
            if (reuse) {
                imageView.layoutParams = layoutParams
            } else {
                val drawable = AppCompatResources.getDrawable(context, R.drawable.page_indicator_dot)!!
                drawable.setTint(primaryColor)
                imageView.setImageDrawable(drawable)
                addView(imageView, layoutParams)
            }
            imageView.alpha = if (i == currentPageIndex) 1f else 0.4f
        }
        contentDescription = context.getString(
            R.string.accessibility_smartspace_page,
            1,
            Integer.valueOf(numPages),
        )
    }
}
