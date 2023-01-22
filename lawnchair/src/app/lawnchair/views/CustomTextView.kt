package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import app.lawnchair.font.FontManager

open class CustomTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    init {
        @Suppress("LeakingThis")
        FontManager.INSTANCE.get(context).overrideFont(this, attrs)
    }
}
