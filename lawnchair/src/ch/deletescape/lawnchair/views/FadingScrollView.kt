package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView


class FadingScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ScrollView(context, attrs, defStyle) {

    var fadeTopEdge = false
    var fadeRightEdge = false
    var fadeBottomEdge = false
    var fadeLeftEdge = false

    override fun getTopFadingEdgeStrength() = if (fadeTopEdge) super.getTopFadingEdgeStrength() else 0f
    override fun getRightFadingEdgeStrength() = if (fadeRightEdge) super.getRightFadingEdgeStrength() else 0f
    override fun getBottomFadingEdgeStrength() = if (fadeBottomEdge) super.getRightFadingEdgeStrength() else 0f
    override fun getLeftFadingEdgeStrength() = if (fadeLeftEdge) super.getRightFadingEdgeStrength() else 0f

}