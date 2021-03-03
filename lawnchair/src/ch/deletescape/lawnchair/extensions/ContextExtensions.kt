package ch.deletescape.lawnchair.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes

fun Context.getThemeDrawable(@AttrRes drawableAttrRes: Int): Drawable? {
    val tempArray = IntArray(1)
    tempArray[0] = drawableAttrRes
    var drawable: Drawable?
    obtainStyledAttributes(null, tempArray).apply {
        drawable = this.getDrawable(0)
        recycle()
    }
    return drawable
}
