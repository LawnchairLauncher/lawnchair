package app.lawnchair.util

import android.graphics.drawable.GradientDrawable

fun GradientDrawable.getCornerRadiiCompat(): FloatArray? = try {
    cornerRadii
} catch (_: NullPointerException) {
    null
}
