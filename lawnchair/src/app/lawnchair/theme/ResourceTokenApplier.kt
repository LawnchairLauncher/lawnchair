package app.lawnchair.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import app.lawnchair.theme.drawable.DrawableTokens
import com.android.launcher3.R
import rikka.layoutinflater.view.LayoutInflaterFactory.OnViewCreatedListener

object ResourceTokenApplier : OnViewCreatedListener {

    override fun onViewCreated(view: View, parent: View?, name: String, context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ResourceTokenOverride)
        for (i in 0 until ta.indexCount) {
            when (val attr = ta.getIndex(i)) {
                R.styleable.ResourceTokenOverride_android_background -> {
                    val resId = ta.getResourceId(attr, 0)
                    val drawable = resolveDrawable(resId, context) ?: continue
                    view.background = drawable
                }
                R.styleable.ResourceTokenOverride_android_src -> {
                    if (view !is ImageView) continue
                    val resId = ta.getResourceId(attr, 0)
                    val drawable = resolveDrawable(resId, context) ?: continue
                    view.setImageDrawable(drawable)
                }
            }
        }
        ta.recycle()
    }

    private fun resolveDrawable(resId: Int, context: Context): Drawable? {
        return DrawableTokens.drawableMapping[resId]?.resolve(context)
    }
}
