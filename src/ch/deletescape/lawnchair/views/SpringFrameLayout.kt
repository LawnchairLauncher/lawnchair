package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.View
import android.widget.FrameLayout

open class SpringFrameLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val springManager = SpringEdgeEffect.Manager(this)

    private val springViews = SparseBooleanArray()

    fun addSpringView(view: View) {
        springViews.put(view.id, true)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        return if (!springViews.get(child.id)) {
            super.drawChild(canvas, child, drawingTime)
        } else {
            springManager.withSpring(canvas) { super.drawChild(canvas, child, drawingTime) }
        }
    }

    fun createEdgeEffectFactory() = springManager.createFactory()
}
