package app.lawnchair.ui.preferences.components.colorpreference

import android.graphics.Color.colorToHSV
import android.graphics.Color.HSVToColor
import android.graphics.Color.parseColor

fun intColorToHsvColorArray(color: Int) =
    FloatArray(size = 3).apply { colorToHSV(color, this) }

fun hsvValuesToIntColor(hue: Float, saturation: Float, brightness: Float): Int =
    HSVToColor(floatArrayOf(hue, saturation, brightness))

fun intColorToColorString(color: Int) =
    String.format("#%06X", 0xFFFFFF and color).removePrefix("#")

fun colorStringToIntColor(colorString: String): Int? =
    try {
        parseColor("#${colorString.removePrefix("#")}")
    } catch (e: IllegalArgumentException) {
        null
    }
