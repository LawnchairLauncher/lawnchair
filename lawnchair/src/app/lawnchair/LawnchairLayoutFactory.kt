package app.lawnchair

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import app.lawnchair.font.FontManager
import com.android.launcher3.BubbleTextView
import com.android.launcher3.views.DoubleShadowBubbleTextView

class LawnchairLayoutFactory(context: Context) : LayoutInflater.Factory2 {

    private val fontManager by lazy { FontManager.INSTANCE.get(context) }
    private val constructorMap = mapOf<String, (Context, AttributeSet) -> View>(
        "Button" to ::Button,
        "TextView" to ::TextView,
        BubbleTextView::class.java.name to ::BubbleTextView,
        DoubleShadowBubbleTextView::class.java.name to ::DoubleShadowBubbleTextView,
    )

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        val view = constructorMap[name]?.let { it(context, attrs) }
        if (view is TextView) {
            runCatching { fontManager.overrideFont(view, attrs) }
        }
        return view
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }
}
