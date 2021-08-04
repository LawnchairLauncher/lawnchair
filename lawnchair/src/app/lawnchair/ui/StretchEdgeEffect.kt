package app.lawnchair.ui

import androidx.core.util.Consumer
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.anim.Interpolators
import kotlin.math.abs

class StretchEdgeEffect(
    private val setShift: Consumer<Float>
) {
    private val springAnim = SpringAnimation(this, DAMPED_SCROLL, 0f).apply {
        spring = SpringForce(0f).apply {
            stiffness = STIFFNESS
            dampingRatio = DAMPING_RATIO
        }
    }
    private var dampedScrollShift = 0f
        set(value) {
            if (field != value) {
                field = value
                setShift.accept(value)
            }
        }

    private fun finishScrollWithVelocity(velocity: Float) {
        springAnim.setStartVelocity(velocity)
        springAnim.setStartValue(dampedScrollShift)
        springAnim.start()
    }

    fun onAbsorb(velocity: Float) {
        finishScrollWithVelocity(velocity * VELOCITY_MULTIPLIER)
    }

    fun onPull(deltaDistance: Float) {
        dampedScrollShift += deltaDistance * (VELOCITY_MULTIPLIER / 6f)
        springAnim.cancel()
    }

    fun onRelease() {
        if (dampedScrollShift != 0f && !springAnim.isRunning) {
            springAnim.setStartValue(dampedScrollShift)
            springAnim.start()
        }
    }

    fun addEndListener(listener: DynamicAnimation.OnAnimationEndListener) {
        springAnim.addEndListener(listener)
    }

    companion object {
        private const val STIFFNESS = SpringForce.STIFFNESS_LOW * 2
        private const val DAMPING_RATIO = SpringForce.DAMPING_RATIO_NO_BOUNCY
        private const val VELOCITY_MULTIPLIER = 0.1f

        private val SCALE_INTERPOLATOR = Interpolators.DEACCEL_2
        private const val MAX_DISTANCE = 0.05f

        private val DAMPED_SCROLL = object : FloatPropertyCompat<StretchEdgeEffect>("value") {
            override fun getValue(obj: StretchEdgeEffect) = obj.dampedScrollShift

            override fun setValue(obj: StretchEdgeEffect, value: Float) {
                obj.dampedScrollShift = value
            }
        }

        fun getScale(shift: Float, size: Float): Float {
            if (shift == 0f) return 1f
            val distance = abs(shift / size).coerceAtMost(MAX_DISTANCE)
            val progress = distance / MAX_DISTANCE
            val interpolatedProgress = SCALE_INTERPOLATOR.getInterpolation(progress)
            return 1f + interpolatedProgress * MAX_DISTANCE
        }

        fun getPivot(shift: Float, size: Float) = if (shift < 0f) size else 0f
    }
}
