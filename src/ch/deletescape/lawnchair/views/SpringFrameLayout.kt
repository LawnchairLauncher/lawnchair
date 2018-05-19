package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.support.animation.DynamicAnimation
import android.support.animation.FloatPropertyCompat
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.View
import android.widget.EdgeEffect
import android.widget.FrameLayout
import ch.deletescape.lawnchair.JavaField

open class SpringFrameLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private var dampedScrollShift = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private val dampedScrollProperty = object : FloatPropertyCompat<SpringFrameLayout>("value") {
        override fun getValue(obj: SpringFrameLayout) = obj.dampedScrollShift

        override fun setValue(obj: SpringFrameLayout, value: Float) {
            obj.dampedScrollShift = value
        }
    }
    private val spring = SpringAnimation(this, dampedScrollProperty, 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var springVelocity by JavaField<Float>(spring, "mVelocity", DynamicAnimation::class.java)
    private var springValue by JavaField<Float>(spring, "mValue", DynamicAnimation::class.java)
    private var springStartValueIsSet by JavaField<Boolean>(spring, "mStartValueIsSet", DynamicAnimation::class.java)

    private val springViews = SparseBooleanArray()

    fun addSpringView(view: View) {
        springViews.put(view.id, true)
    }

    private fun releaseSpring(velocity: Float) {
        springVelocity = velocity
        springValue = dampedScrollShift
        springStartValueIsSet = true
        spring.start()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (dampedScrollShift == 0f || !springViews.get(child.id)) {
            return super.drawChild(canvas, child, drawingTime)
        }
        canvas.translate(0f, dampedScrollShift)
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.translate(0f, -dampedScrollShift)
        return result
    }

    fun createEdgeEffectFactory() = SpringEdgeEffectFactory()

    inner class SpringEdgeEffect(context: Context, private val velocityMultiplier: Float) : EdgeEffect(context) {

        private var distance = 0f

        override fun draw(canvas: Canvas) = false

        override fun onAbsorb(velocity: Int) {
            releaseSpring(velocityMultiplier * velocity)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            distance += deltaDistance * (velocityMultiplier / 3)
            dampedScrollShift = distance * height
        }

        override fun onRelease() {
            distance = 0f
            releaseSpring(0f)
        }
    }

    inner class SpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

        override fun createEdgeEffect(view: RecyclerView?, direction: Int) = when (direction) {
            DIRECTION_TOP -> SpringEdgeEffect(context, 0.3f)
            DIRECTION_BOTTOM -> SpringEdgeEffect(context, -0.3f)
            else -> super.createEdgeEffect(view, direction)
        }
    }
}