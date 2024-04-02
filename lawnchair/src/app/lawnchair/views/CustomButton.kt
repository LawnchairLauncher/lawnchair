package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import app.lawnchair.font.FontManager

class CustomButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatButton(context, attrs) {

    init {
        FontManager.INSTANCE.get(context).overrideFont(this, attrs)
    }
}
