package app.lawnchair.search.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.View
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.search.LawnchairSearchAdapterProvider
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground?,
    val viewType: Int,
) : BaseAllAppsAdapter.AdapterItem(viewType) {

    override fun isSameAs(other: BaseAllAppsAdapter.AdapterItem): Boolean {
        return other is SearchAdapterItem && other.searchTarget.id == searchTarget.id
    }

    override fun isContentSame(other: BaseAllAppsAdapter.AdapterItem): Boolean {
        if (other !is SearchAdapterItem) return false
        return other.searchTarget == searchTarget &&
            other.viewType == viewType
    }

    fun setRippleEffect(child: View) {
        val shape = RoundRectShape(background?.cornerRadii, null, null)
        val maskDrawable = ShapeDrawable(shape).apply {
            paint.color = Color.WHITE
        }
        val rippleColor = background?.focusHighlight ?: background?.groupHighlight ?: 0
        val inset = child.resources.getDimensionPixelSize(R.dimen.search_decoration_padding)
        val insetMask = InsetDrawable(maskDrawable, 0, inset, 0, inset)
        child.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, insetMask)
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
