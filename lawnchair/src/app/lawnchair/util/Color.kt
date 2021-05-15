package app.lawnchair.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

object Color {
    @JvmStatic
    fun parseAccentColorInt(accentColor: Int, context: Context): Int {
        if (accentColor == 0) return context.systemAccentColor

        return when (context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> lightenColor(accentColor)
            else -> accentColor
        }
    }

    @JvmStatic @ColorInt
    fun lightenColor(@ColorInt color: Int): Int {
        val outHsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(color, outHsl)
        outHsl[2] = 0.7F
        return ColorUtils.HSLToColor(outHsl)
    }
}