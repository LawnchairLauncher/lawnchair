package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Utilities
import com.android.launcher3.views.SpringRelativeLayout

@Suppress("LeakingThis")
sealed class StretchRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SpringRelativeLayout(context, attrs, defStyleAttr) {

    protected val edgeEffectTop = StretchEdgeEffect(context, { invalidate() }, { postInvalidateOnAnimation() })
    protected val edgeEffectBottom = StretchEdgeEffect(context, { invalidate() }, { postInvalidateOnAnimation() })

    init {
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        if (Utilities.ATLEAST_S) {
            super.draw(canvas)
        } else {
            if (!edgeEffectTop.isFinished || !edgeEffectBottom.isFinished) {
                val save = canvas.save()
                edgeEffectTop.setSize(width, height)
                edgeEffectTop.applyStretch(canvas, StretchEdgeEffect.POSITION_TOP)
                edgeEffectBottom.setSize(width, height)
                edgeEffectBottom.applyStretch(canvas, StretchEdgeEffect.POSITION_BOTTOM)
                super.draw(canvas)
                canvas.restoreToCount(save)
            } else {
                super.draw(canvas)
            }
        }
    }

    override fun absorbSwipeUpVelocity(velocity: Int) {
        if (Utilities.ATLEAST_S) {
            super.absorbSwipeUpVelocity(velocity)
        } else {
            edgeEffectBottom.onAbsorb(velocity)
        }
    }

    override fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        if (Utilities.ATLEAST_S) return super.createEdgeEffectFactory()
        return object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return when (direction) {
                    DIRECTION_TOP -> edgeEffectTop
                    DIRECTION_BOTTOM -> edgeEffectBottom
                    else -> super.createEdgeEffect(view, direction)
                }
            }
        }
    }
}
