package ch.deletescape.lawnchair.views

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.support.animation.DynamicAnimation
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.EdgeEffect
import ch.deletescape.lawnchair.JavaField
import ch.deletescape.lawnchair.KFloatProperty
import ch.deletescape.lawnchair.KFloatPropertyCompat
import ch.deletescape.lawnchair.clamp
import com.android.launcher3.Utilities
import com.android.launcher3.touch.OverScroll
import kotlin.reflect.KMutableProperty0

class SpringEdgeEffect(
        context: Context,
        private val getMax: () -> Int,
        private val target: KMutableProperty0<Float>,
        private val activeEdge: KMutableProperty0<SpringEdgeEffect?>,
        private val velocityMultiplier: Float) : EdgeEffect(context) {

    private val prefs = Utilities.getLawnchairPrefs(context)

    private val shiftProperty = KFloatProperty(target, "value")
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
        activeEdge.set(this)
        distance += deltaDistance * (velocityMultiplier * 2)
        target.set(OverScroll.dampedScroll(distance * getMax(), getMax()).toFloat())
    }

    override fun onRelease() {
        distance = 0f
        releaseSpring(0f)
    }

    private fun releaseSpring(velocity: Float) {
        if (prefs.enablePhysics) {
            springVelocity = velocity
            springValue = target.get()
            springStartValueIsSet = true
            spring.start()
        } else {
            ObjectAnimator.ofFloat(this, shiftProperty, 0f)
                    .setDuration(100)
                    .start()
        }
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

        private fun clampShift(value: Float, max: Float): Float {
            return value.clamp(-max, max)
        }

        var activeEdgeX: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
            }
        var activeEdgeY: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
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
                    DIRECTION_LEFT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, 0.3f)
                    DIRECTION_TOP -> SpringEdgeEffect(view.context, view::getHeight, ::shiftY, ::activeEdgeY, 0.3f)
                    DIRECTION_RIGHT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, -0.3f)
                    DIRECTION_BOTTOM -> SpringEdgeEffect(view.context, view::getWidth, ::shiftY, ::activeEdgeY, -0.3f)
                    else -> super.createEdgeEffect(recyclerView, direction)
                }
            }
        }
    }
}