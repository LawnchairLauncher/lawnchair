package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R

open class AllAppsStretchLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : StretchRelativeLayout(context, attrs, defStyleAttr) {

    private val childEffect = StretchEdgeEffect { invalidate() }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child.id != R.id.apps_list_view) {
            return super.drawChild(canvas, child, drawingTime)
        }
        var result = false
        childEffect.draw(canvas, child.height.toFloat()) {
            result = super.drawChild(canvas, child, drawingTime)
        }
        return result
    }

    override fun createEdgeEffectFactory(): RecyclerView.EdgeEffectFactory {
        return object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return when (direction) {
                    DIRECTION_TOP -> effect.EdgeEffectProxy(context, direction, view)
                    DIRECTION_BOTTOM -> childEffect.EdgeEffectProxy(context, direction, view)
                    else -> super.createEdgeEffect(view, direction)
                }
            }
        }
    }
}
