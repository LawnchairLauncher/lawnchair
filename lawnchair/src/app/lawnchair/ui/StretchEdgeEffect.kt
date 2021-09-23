package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.widget.EdgeEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.anim.Interpolators
import kotlin.math.abs

class StretchEdgeEffect(private val onUpdate: () -> Unit = {}) {
    private val springAnim = SpringAnimation(this, DAMPED_SCROLL, 0f).apply {
        spring = SpringForce(0f).apply {
            stiffness = STIFFNESS
            dampingRatio = DAMPING_RATIO
        }
    }
    var shift = 0f
        private set(value) {
            if (field != value) {
                field = value
                _shiftState.value = value
                onUpdate()
            }
        }
    private val _shiftState = mutableStateOf(0f)
    val shiftState: State<Float> = _shiftState

    private fun finishScrollWithVelocity(velocity: Float) {
        springAnim.setStartVelocity(velocity)
        springAnim.setStartValue(shift)
        springAnim.start()
    }

    fun onAbsorb(velocity: Float) {
        finishScrollWithVelocity(velocity * VELOCITY_MULTIPLIER)
    }

    fun onPull(deltaDistance: Float) {
        shift += deltaDistance * (VELOCITY_MULTIPLIER / 6f)
        springAnim.cancel()
    }

    fun onRelease() {
        if (shift != 0f && !springAnim.isRunning) {
            springAnim.setStartValue(shift)
            springAnim.start()
        }
    }

    inline fun draw(canvas: Canvas, size: Float, crossinline block: (Canvas) -> Unit) {
        val shiftValue = shift
        val scaleY = getScale(shiftValue, size)
        if (scaleY != 1f) {
            val save = canvas.save()
            canvas.scale(1f, scaleY, 0f, getPivot(shiftValue, size))
            block(canvas)
            canvas.restoreToCount(save)
        } else {
            block(canvas)
        }
    }

    inline fun draw(scope: DrawScope, crossinline block: () -> Unit) {
        val shiftValue = shiftState.value
        val height = scope.size.height
        val scaleY = getScale(shiftValue, height)
        if (scaleY != 1f) {
            val pivotY = getPivot(shiftValue, height)
            scope.scale(1f, scaleY, pivot = Offset(0f, pivotY)) {
                block()
            }
        } else {
            block()
        }
    }

    fun addEndListener(listener: DynamicAnimation.OnAnimationEndListener) {
        springAnim.addEndListener(listener)
    }

    fun createEdgeEffectFactory(context: Context): RecyclerView.EdgeEffectFactory {
        return StretchEdgeEffectFactory(context)
    }

    private inner class StretchEdgeEffectFactory(private val context: Context) : RecyclerView.EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            return when (direction) {
                DIRECTION_TOP, DIRECTION_BOTTOM -> EdgeEffectProxy(context, direction, view)
                else -> super.createEdgeEffect(view, direction)
            }
        }
    }

    inner class EdgeEffectProxy(context: Context, private val direction: Int, private val view: RecyclerView) : EdgeEffect(context) {
        private val multiplier = when (direction) {
            RecyclerView.EdgeEffectFactory.DIRECTION_TOP -> 1f
            RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM -> -1f
            else -> throw IllegalArgumentException("invalid direction $direction")
        }

        override fun isFinished(): Boolean {
            return when (direction) {
                RecyclerView.EdgeEffectFactory.DIRECTION_TOP -> shift <= 0f
                RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM -> shift >= 0f
                else -> true
            }
        }

        override fun onPull(deltaDistance: Float) {
            this@StretchEdgeEffect.onPull(deltaDistance * multiplier * view.height)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            this@StretchEdgeEffect.onPull(deltaDistance * multiplier * view.height)
        }

        override fun onAbsorb(velocity: Int) {
            this@StretchEdgeEffect.onAbsorb(velocity * multiplier)
        }

        override fun onRelease() {
            this@StretchEdgeEffect.onRelease()
        }
    }

    companion object {
        private const val STIFFNESS = SpringForce.STIFFNESS_LOW * 2
        private const val DAMPING_RATIO = SpringForce.DAMPING_RATIO_NO_BOUNCY
        private const val VELOCITY_MULTIPLIER = 0.1f

        private val SCALE_INTERPOLATOR = Interpolators.DEACCEL_2
        private const val MAX_DISTANCE = 0.05f

        private val DAMPED_SCROLL = object : FloatPropertyCompat<StretchEdgeEffect>("value") {
            override fun getValue(obj: StretchEdgeEffect) = obj.shift

            override fun setValue(obj: StretchEdgeEffect, value: Float) {
                obj.shift = value
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
