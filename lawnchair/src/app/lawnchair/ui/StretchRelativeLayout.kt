package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.widget.EdgeEffect
import android.widget.RelativeLayout
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.round

@Suppress("LeakingThis")
open class StretchRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val effect = StretchEdgeEffect { shift -> this.shift = shift }
    private var shift = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    @JvmField
    protected val mSpringViews = SparseBooleanArray()

    init {
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        val height = height.toFloat()
        val scaleY = StretchEdgeEffect.getScale(shift, height)
        if (scaleY != 1f) {
            val save = canvas.save()
            canvas.scale(1f, scaleY, 0f, StretchEdgeEffect.getPivot(shift, height))
            super.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            super.draw(canvas)
        }
    }

    fun addSpringView(id: Int) {
        mSpringViews.put(id, true)
    }

    fun removeSpringView(id: Int) {
        mSpringViews.delete(id)
    }

    open fun getCanvasClipTopForOverscroll() = 0

    protected open fun setDampedScrollShift(shift: Float) {

    }

    protected open fun finishWithShiftAndVelocity(
        shift: Float, velocity: Float,
        listener: OnAnimationEndListener
    ) {
        effect.onAbsorb(round(velocity * 800f / 135f))
        effect.addEndListener(listener)
    }

    fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        return StretchEdgeEffectFactory()
    }

    private inner class StretchEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            return when (direction) {
                DIRECTION_TOP, DIRECTION_BOTTOM -> EdgeEffectProxy(context, direction)
                else -> super.createEdgeEffect(view, direction)
            }
        }
    }

    private inner class EdgeEffectProxy(context: Context, private val direction: Int) : EdgeEffect(context) {
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
            effect.onPull(deltaDistance * height)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            effect.onPull(deltaDistance * height * multiplier)
        }

        override fun onAbsorb(velocity: Int) {
            effect.onAbsorb(velocity * multiplier)
        }

        override fun onRelease() {
            effect.onRelease()
        }
    }
}
