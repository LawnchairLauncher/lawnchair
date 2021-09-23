package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.SparseBooleanArray
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
    protected val effect = StretchEdgeEffect(this::invalidate)

    @JvmField
    protected val mSpringViews = SparseBooleanArray()

    init {
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        effect.draw(canvas, height.toFloat()) {
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

    open fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        return effect.createEdgeEffectFactory(context)
    }
}
