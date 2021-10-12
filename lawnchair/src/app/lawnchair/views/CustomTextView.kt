package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import app.lawnchair.font.FontManager

class CustomTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextView(context, attrs) {

    init {
        FontManager.INSTANCE.get(context).overrideFont(this, attrs)
    }
}
