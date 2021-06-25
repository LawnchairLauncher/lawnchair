package app.lawnchair.ui.theme

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

const val LAWNCHAIR_BLUE: Long = 0xFF007FFF

@JvmOverloads
fun Context.getAccentColor(darkTheme: Boolean = Themes.getAttrBoolean(this, R.attr.isMainColorDark)): Int {
    val prefs = PreferenceManager.getInstance(this)
    val customAccentColor = prefs.accentColor.get()
    val useSystemAccent = prefs.useSystemAccent.get()
    val accentColor = if (useSystemAccent) this.getSystemAccent(darkTheme = darkTheme) else customAccentColor

    return if (darkTheme) lightenColor(accentColor) else accentColor
}

@ColorInt
fun lightenColor(@ColorInt color: Int): Int {
    var newColor = color

    while (ColorUtils.calculateContrast(newColor, 0xFF000000.toInt()) < 6.5) {
        val outHsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(newColor, outHsl)
        outHsl[2] += 0.05F
        newColor = ColorUtils.HSLToColor(outHsl)
    }

    return newColor
}

@Suppress("DEPRECATION")
fun Context.getSystemAccent(darkTheme: Boolean): Int {
    val res = resources
    return if (Utilities.ATLEAST_S) {
        val colorName = if (darkTheme) "system_accent1_100" else "system_accent1_600"
        val colorId = res.getIdentifier(colorName, "color", "android")
        res.getColor(colorId)
    } else {
        val typedValue = TypedValue()
        val contextWrapper = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_DayNight)
        contextWrapper.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        typedValue.data
    }
}