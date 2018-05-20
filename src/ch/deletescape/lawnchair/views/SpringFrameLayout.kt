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

    private var dampedScrollShiftX = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private var dampedScrollShiftY = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private val dampedScrollPropertyX = object : FloatPropertyCompat<SpringFrameLayout>("value") {
        override fun getValue(obj: SpringFrameLayout) = obj.dampedScrollShiftX

        override fun setValue(obj: SpringFrameLayout, value: Float) {
            obj.dampedScrollShiftX = value
        }
    }
    private val dampedScrollPropertyY = object : FloatPropertyCompat<SpringFrameLayout>("value") {
        override fun getValue(obj: SpringFrameLayout) = obj.dampedScrollShiftY

        override fun setValue(obj: SpringFrameLayout, value: Float) {
            obj.dampedScrollShiftY = value
        }
    }
    private val springX = SpringAnimation(this, dampedScrollPropertyX, 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var springVelocityX by JavaField<Float>(springX, "mVelocity", DynamicAnimation::class.java)
    private var springValueX by JavaField<Float>(springX, "mValue", DynamicAnimation::class.java)
    private var springStartValueIsSetX by JavaField<Boolean>(springX, "mStartValueIsSet", DynamicAnimation::class.java)
    private val springY = SpringAnimation(this, dampedScrollPropertyY, 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var springVelocityY by JavaField<Float>(springY, "mVelocity", DynamicAnimation::class.java)
    private var springValueY by JavaField<Float>(springY, "mValue", DynamicAnimation::class.java)
    private var springStartValueIsSetY by JavaField<Boolean>(springY, "mStartValueIsSet", DynamicAnimation::class.java)

    private val springViews = SparseBooleanArray()

    fun addSpringView(view: View) {
        springViews.put(view.id, true)
    }

    private fun releaseSpringX(velocity: Float) {
        springVelocityX = velocity
        springValueX = dampedScrollShiftX
        springStartValueIsSetX = true
        springX.start()
    }

    private fun releaseSpringY(velocity: Float) {
        springVelocityY = velocity
        springValueY = dampedScrollShiftY
        springStartValueIsSetY = true
        springY.start()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if ((dampedScrollShiftX == 0f && dampedScrollShiftY == 0f) || !springViews.get(child.id)) {
            return super.drawChild(canvas, child, drawingTime)
        }
        canvas.translate(dampedScrollShiftX, dampedScrollShiftY)
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.translate(-dampedScrollShiftX, -dampedScrollShiftY)
        return result
    }

    fun createEdgeEffectFactory() = SpringEdgeEffectFactory()

    inner class SpringEdgeEffectX(context: Context, private val velocityMultiplier: Float) : EdgeEffect(context) {

        private var distanceX = 0f

        override fun draw(canvas: Canvas) = false

        override fun onAbsorb(velocity: Int) {
            releaseSpringX(velocityMultiplier * velocity)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            distanceX += deltaDistance * (velocityMultiplier / 3)
            dampedScrollShiftX = distanceX * height
        }

        override fun onRelease() {
            distanceX = 0f
            releaseSpringX(0f)
        }
    }

    inner class SpringEdgeEffectY(context: Context, private val velocityMultiplier: Float) : EdgeEffect(context) {

        private var distanceY = 0f

        override fun draw(canvas: Canvas) = false

        override fun onAbsorb(velocity: Int) {
            releaseSpringY(velocityMultiplier * velocity)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            distanceY += deltaDistance * (velocityMultiplier / 3)
            dampedScrollShiftY = distanceY * height
        }

        override fun onRelease() {
            distanceY= 0f
            releaseSpringY(0f)
        }
    }

    inner class SpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

        override fun createEdgeEffect(view: RecyclerView?, direction: Int) = when (direction) {
            DIRECTION_LEFT -> SpringEdgeEffectX(context, 0.3f)
            DIRECTION_TOP -> SpringEdgeEffectY(context, 0.3f)
            DIRECTION_RIGHT -> SpringEdgeEffectX(context, -0.3f)
            DIRECTION_BOTTOM -> SpringEdgeEffectY(context, -0.3f)
            else -> super.createEdgeEffect(view, direction)
        }
    }
}