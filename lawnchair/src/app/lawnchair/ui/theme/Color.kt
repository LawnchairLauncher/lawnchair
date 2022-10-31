package app.lawnchair.ui.theme

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.theme.surfaceColorAtElevation
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

@JvmOverloads
fun Context.getAccentColor(darkTheme: Boolean = Themes.getAttrBoolean(this, R.attr.isMainColorDark)): Int {
    return ColorTokens.ColorAccent.resolveColor(this, if (darkTheme) UiColorMode.Dark else UiColorMode.Light)
}

@ColorInt
fun lightenColor(@ColorInt color: Int): Int {
    var newColor = color
    val outHsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, outHsl)

    while (ColorUtils.calculateContrast(newColor, 0xFF000000.toInt()) < 6.5) {
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
        var propertyValue = Utilities.getSystemProperty("persist.sys.theme.accentcolor", "")
        if (!TextUtils.isEmpty(propertyValue)) {
            if (!propertyValue.startsWith('#')) {
                propertyValue = "#$propertyValue"
            }
            try {
                return Color.parseColor(propertyValue)
            } catch (_: IllegalArgumentException) {
            }
        }

        val typedValue = TypedValue()
        val theme = if (darkTheme) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
        val contextWrapper = ContextThemeWrapper(this, theme)
        contextWrapper.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        typedValue.data
    }
}

@Composable
fun dividerColor() = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp)
