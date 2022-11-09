package app.lawnchair.theme.color

import android.content.Context
import android.util.Log
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.ResourceToken
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.toAndroidColor
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.androidinternal.graphics.cam.Cam
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.monet.theme.ColorScheme

sealed interface ColorToken : ResourceToken<Color> {
    fun resolveColor(context: Context) = resolveColor(context, UiColorMode(Themes.getAttrInteger(context, R.attr.uiColorMode)))
    fun resolveColor(context: Context, uiColorMode: UiColorMode): Int {
        val themeProvider = ThemeProvider.INSTANCE.get(context)
        return resolveColor(context, themeProvider.colorScheme, uiColorMode)
    }
    fun resolveColor(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Int {
        return try {
            resolve(context, scheme, uiColorMode).toAndroidColor()
        } catch (t: Throwable) {
            Log.e("ColorToken", "failed to resolve color", t)
            android.graphics.Color.WHITE
        }
    }
}

data class SwatchColorToken(
    private val swatch: Swatch,
    private val shade: Shade
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        val swatch = when (swatch) {
            Swatch.Neutral1 -> scheme.neutral1
            Swatch.Neutral2 -> scheme.neutral2
            Swatch.Accent1 -> scheme.accent1
            Swatch.Accent2 -> scheme.accent2
            Swatch.Accent3 -> scheme.accent3
        }
        return swatch[shade.lightness]!!
    }
}

data class DayNightColorToken(
    private val lightToken: ColorToken,
    private val darkToken: ColorToken
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        return if (uiColorMode.isDarkTheme) {
            darkToken.resolve(context, scheme, uiColorMode)
        } else {
            lightToken.resolve(context, scheme, uiColorMode)
        }
    }

    fun inverse(): DayNightColorToken {
        return DayNightColorToken(darkToken, lightToken)
    }
}

data class DarkTextColorToken(
    private val lightToken: ColorToken,
    private val darkToken: ColorToken
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        return if (uiColorMode.isDarkText) {
            darkToken.resolve(context, scheme, uiColorMode)
        } else {
            lightToken.resolve(context, scheme, uiColorMode)
        }
    }
}

data class StaticColorToken(
    private val color: Long
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        return AndroidColor(color.toInt())
    }
}

data class SetAlphaColorToken(
    private val token: ColorToken,
    private val alpha: Float
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        val color = token.resolveColor(context, scheme, uiColorMode)
        return AndroidColor(ColorUtils.setAlphaComponent(color, (alpha * 255).toInt()))
    }
}

data class SetLStarColorToken(
    private val token: ColorToken,
    private val lStar: Double
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        val color = token.resolveColor(context, scheme, uiColorMode)
        val cam = Cam.fromInt(color)
        return AndroidColor(Cam.getInt(cam.hue, cam.chroma, lStar.toFloat()))
    }
}

class WithContextColorToken(
    private val token: ColorToken,
    private val transform: ColorToken.(Context) -> ColorToken
) : ColorToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): Color {
        return transform(token, context).resolve(context, scheme, uiColorMode)
    }
}

enum class Swatch { Neutral1, Neutral2, Accent1, Accent2, Accent3 }

@JvmInline
value class Shade private constructor(val lightness: Int) {
    @Suppress("unused")
    companion object {
        val S0 = Shade(0)
        val S10 = Shade(10)
        val S20 = Shade(20)
        val S50 = Shade(50)
        val S100 = Shade(100)
        val S200 = Shade(200)
        val S300 = Shade(300)
        val S400 = Shade(400)
        val S500 = Shade(500)
        val S600 = Shade(600)
        val S650 = Shade(650)
        val S700 = Shade(700)
        val S800 = Shade(800)
        val S900 = Shade(900)
        val S950 = Shade(950)
        val S1000 = Shade(1000)
    }
}
