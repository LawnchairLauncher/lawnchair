package app.lawnchair.ui.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

@JvmOverloads
fun Context.getAccentColor(darkTheme: Boolean = Themes.getAttrBoolean(this, R.attr.isMainColorDark)): Int {
    val prefs = PreferenceManager.getInstance(this)
    val customColor = prefs.accentColor.get()
    return when {
        customColor == 0 -> getDefaultAccentColor(darkTheme)
        darkTheme -> lightenColor(customColor)
        else -> customColor
    }
}

@ColorInt
fun lightenColor(@ColorInt color: Int): Int {
    val outHsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, outHsl)
    outHsl[2] = 0.7F
    return ColorUtils.HSLToColor(outHsl)
}

@Suppress("DEPRECATION") // use Resources.getColor(int) directly because we don't need the theme
fun Context.getDefaultAccentColor(darkTheme: Boolean): Int {
    val res = resources
    return when {
        Utilities.ATLEAST_S -> {
            val colorName = if (darkTheme) "system_accent1_100" else "system_accent1_600"
            val colorId = res.getIdentifier(colorName, "color", "android")
            res.getColor(colorId)
        }
        darkTheme -> res.getColor(R.color.primary_200)
        else -> res.getColor(R.color.primary_500)
    }
}
