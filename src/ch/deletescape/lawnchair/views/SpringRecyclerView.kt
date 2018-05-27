package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View

class SpringRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {

    private val springManager = SpringEdgeEffect.Manager(this)

    init {
        edgeEffectFactory = springManager.createFactory()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        return springManager.withSpring(canvas) { super.drawChild(canvas, child, drawingTime) }
    }
}
