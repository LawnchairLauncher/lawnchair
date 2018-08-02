package ch.deletescape.lawnchair.colors.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine

@SuppressLint("ViewConstructor")
class ColorPreviewView(context: Context, attrs: AttributeSet) : TextView(context, attrs) {

    var colorResolver: ColorEngine.ColorResolver? = null
        set(value) {
            if (value == null) throw IllegalArgumentException("colorResolver must not be null")
            setBackgroundColor(value.resolveColor())
            setTextColor(value.computeForegroundColor())
            text = value.getDisplayName()
            field = value
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }
}
