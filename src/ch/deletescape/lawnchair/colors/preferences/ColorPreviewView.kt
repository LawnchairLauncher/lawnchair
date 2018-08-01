package ch.deletescape.lawnchair.colors.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import ch.deletescape.lawnchair.colors.ColorEngine

@SuppressLint("ViewConstructor")
class ColorPreviewView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var colorResolver: ColorEngine.ColorResolver? = null
        set(value) {
            if (value == null) throw IllegalArgumentException("colorResolver must not be null")
//            setTextColor(value.computeForegroundColor())
            setBackgroundColor(value.resolveColor())
//            text = value.getDisplayName()
            field = value
        }
}
