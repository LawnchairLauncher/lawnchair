package ch.deletescape.lawnchair.views

import android.graphics.Canvas
import android.support.animation.DynamicAnimation
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.EdgeEffect
import ch.deletescape.lawnchair.JavaField
import ch.deletescape.lawnchair.KFloatPropertyCompat
import kotlin.reflect.KMutableProperty0

class SpringEdgeEffect(
        private val view: View,
        private val target: KMutableProperty0<Float>,
        private val velocityMultiplier: Float) : EdgeEffect(view.context) {

    private val spring = SpringAnimation(this, KFloatPropertyCompat(target, "value"), 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var springVelocity by JavaField<Float>(spring, "mVelocity", DynamicAnimation::class.java)
    private var springValue by JavaField<Float>(spring, "mValue", DynamicAnimation::class.java)
    private var springStartValueIsSet by JavaField<Boolean>(spring, "mStartValueIsSet", DynamicAnimation::class.java)
    private var distance = 0f

    override fun draw(canvas: Canvas) = false

    override fun onAbsorb(velocity: Int) {
        releaseSpring(velocityMultiplier * velocity)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        distance += deltaDistance * (velocityMultiplier / 3)
        target.set(distance * view.height)
    }

    override fun onRelease() {
        distance = 0f
        releaseSpring(0f)
    }

    private fun releaseSpring(velocity: Float) {
        springVelocity = velocity
        springValue = target.get()
        springStartValueIsSet = true
        spring.start()
    }

    class Manager(val view: View) {

        var shiftX = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }
        var shiftY = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }

        inline fun withSpring(canvas: Canvas, body: () -> Boolean): Boolean {
            val result: Boolean
            if (shiftX == 0f && shiftY == 0f) {
                result = body()
            } else {
                canvas.translate(shiftX, shiftY)
                result = body()
                canvas.translate(-shiftX, -shiftY)
            }
            return result
        }

        fun createFactory() = SpringEdgeEffectFactory()

        inner class SpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

            override fun createEdgeEffect(recyclerView: RecyclerView?, direction: Int): EdgeEffect {
                return when (direction) {
                    DIRECTION_LEFT -> SpringEdgeEffect(view, ::shiftX, 0.3f)
                    DIRECTION_TOP -> SpringEdgeEffect(view, ::shiftY, 0.3f)
                    DIRECTION_RIGHT -> SpringEdgeEffect(view, ::shiftX, -0.3f)
                    DIRECTION_BOTTOM -> SpringEdgeEffect(view, ::shiftY, -0.3f)
                    else -> super.createEdgeEffect(recyclerView, direction)
                }
            }
        }
    }
}