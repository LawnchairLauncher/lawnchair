package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import app.lawnchair.font.FontManager

class CustomButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Button(context, attrs) {

    init {
        FontManager.INSTANCE.get(context).overrideFont(this, attrs)
    }
}
