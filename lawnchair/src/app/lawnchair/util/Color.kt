package app.lawnchair.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

object Color {
    @JvmOverloads
    @JvmStatic
    fun parseAccentColorInt(
        accentColor: Int,
        context: Context,
        isDarkMode: Boolean = isDarkMode(context)
    ): Int {
        if (accentColor == 0) return context.systemAccentColor

        return when {
            isDarkMode -> lightenColor(accentColor)
            else -> accentColor
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        return when (context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
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
