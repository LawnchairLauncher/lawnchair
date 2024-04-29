package app.lawnchair.search.adapter

import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.View
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.search.LawnchairSearchAdapterProvider
import com.android.launcher3.allapps.BaseAllAppsAdapter

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground?,
    val viewType: Int,
) : BaseAllAppsAdapter.AdapterItem(viewType) {

    fun setRippleEffect(child: View) {
        val shape = RoundRectShape(background?.cornerRadii, null, null)
        val shapeDrawable = ShapeDrawable(shape)
        val colorDefault = background?.groupHighlight ?: 0
        val colorPressed = background?.focusHighlight ?: 0

        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_pressed),
            ),
            intArrayOf(
                colorPressed,
                colorDefault,
            ),
        )

        shapeDrawable.paint.color = colorDefault
        val inset = 3
        val insetDrawable = InsetDrawable(shapeDrawable, 0, inset, 0, inset)
        val rippleDrawable = RippleDrawable(colorStateList, insetDrawable, null)
        child.background = rippleDrawable
    }

    companion object {

        fun createAdapterItem(
            target: SearchTargetCompat,
            background: SearchItemBackground?,
        ): SearchAdapterItem? {
            val type = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType] ?: return null
            return SearchAdapterItem(target, background, type)
        }
    }
}
