package app.lawnchair.util

import android.graphics.drawable.GradientDrawable

fun GradientDrawable.getCornerRadiiCompat(): FloatArray? = runCatching { cornerRadii }.getOrNull()
