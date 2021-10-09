package app.lawnchair.util

import android.graphics.drawable.GradientDrawable

fun GradientDrawable.getCornerRadiiCompat(): FloatArray? {
    return try {
        cornerRadii
    } catch (e: NullPointerException) {
        null
    }
}
