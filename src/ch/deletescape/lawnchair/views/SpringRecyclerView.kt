package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet

open class SpringRecyclerView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : RecyclerView(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val springManager = SpringEdgeEffect.Manager(this)

    init {
        edgeEffectFactory = springManager.createFactory()
    }

    override fun dispatchDraw(canvas: Canvas) {
        springManager.withSpring(canvas) {
            super.dispatchDraw(canvas)
            false
        }
    }
}
