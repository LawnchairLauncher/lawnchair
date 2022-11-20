package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Utilities
import com.android.launcher3.workprofile.PersonalWorkPagedView

open class StretchRecyclerViewContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : StretchRelativeLayout(context, attrs, defStyleAttr) {

    private val childEffect = StretchEdgeEffect(context, { invalidate() }, { postInvalidateOnAnimation() })

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (Utilities.ATLEAST_S || (child !is RecyclerView && child !is PersonalWorkPagedView)) {
            return super.drawChild(canvas, child, drawingTime)
        }

        return if (!childEffect.isFinished) {
            val save = canvas.save()
            clipChild(canvas, child)
            childEffect.setSize(width, height)
            childEffect.applyStretch(canvas, StretchEdgeEffect.POSITION_BOTTOM)
            val result = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
            result
        } else {
            super.drawChild(canvas, child, drawingTime)
        }
    }

    override fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        if (Utilities.ATLEAST_S) return super.createEdgeEffectFactory()
        return object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return when (direction) {
                    DIRECTION_TOP -> edgeEffectTop
                    DIRECTION_BOTTOM -> childEffect
                    else -> super.createEdgeEffect(view, direction)
                }
            }
        }
    }

    open fun clipChild(canvas: Canvas, child: View) {
        canvas.clipRect(child.left, child.top, child.right, child.bottom)
    }
}
