package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.widget.RelativeLayout
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Utilities
import com.android.launcher3.views.SpringRelativeLayout
import kotlin.math.round

@Suppress("LeakingThis")
open class StretchRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SpringRelativeLayout(context, attrs, defStyleAttr) {
    protected val effect = StretchEdgeEffect(this::invalidate)

    @JvmField
    protected val mSpringViews = SparseBooleanArray()

    init {
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        if (Utilities.ATLEAST_S) {
            super.draw(canvas)
        } else {
            effect.draw(canvas, height.toFloat()) {
                super.draw(canvas)
            }
        }
    }

    override fun absorbSwipeUpVelocity(velocity: Int) {
        if (Utilities.ATLEAST_S) {
            super.absorbSwipeUpVelocity(velocity)
        } else {
            effect.onAbsorb(-round(velocity * 400f / 135f))
        }
    }

    override fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        if (Utilities.ATLEAST_S) return super.createEdgeEffectFactory()
        return effect.createEdgeEffectFactory(context)
    }
}
