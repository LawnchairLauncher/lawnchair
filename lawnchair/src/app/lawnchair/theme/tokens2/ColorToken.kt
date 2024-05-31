package app.lawnchair.theme.tokens2

import android.content.Context
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.colorscheme.LightDarkScheme
import com.android.launcher3.R
import com.android.launcher3.util.Themes

interface ColorToken<T> {
    fun resolve(context: Context): T = resolve(context, UiColorMode(Themes.getAttrInteger(context, R.attr.uiColorMode)))
    fun resolve(context: Context, uiColorMode: UiColorMode): T {
        val themeProvider = ThemeProvider.INSTANCE.get(context)
        return resolve(context, themeProvider.colorScheme, uiColorMode)
    }
    fun resolve(context: Context, scheme: LightDarkScheme, uiColorMode: UiColorMode): T
}


class MaterialColor(
    val color: String
) : ColorToken<Int> {
    override fun resolve(context: Context, scheme: LightDarkScheme, uiColorMode: UiColorMode): Int {
        return if (uiColorMode.isDarkTheme) {
            scheme.darkScheme.get(color)
        } else {
            scheme.lightScheme.get(color)
        }
    }
}

object ColorTokens2 {
    val primary = MaterialColor("primary")
    val onPrimary = MaterialColor("onPrimary")
    val primaryContainer = MaterialColor("primaryContainer")
    val onPrimaryContainer = MaterialColor("onPrimaryContainer")
    val inversePrimary = MaterialColor("inversePrimary")
    val secondary = MaterialColor("secondary")
    val onSecondary = MaterialColor("onSecondary")
    val secondaryContainer = MaterialColor("secondaryContainer")
    val onSecondaryContainer = MaterialColor("onSecondaryContainer")
    val tertiary = MaterialColor("tertiary")
    val onTertiary = MaterialColor("onTertiary")
    val tertiaryContainer = MaterialColor("tertiaryContainer")
    val onTertiaryContainer = MaterialColor("onTertiaryContainer")
    val background = MaterialColor("background")
    val onBackground = MaterialColor("onBackground")
    val surface = MaterialColor("surface")
    val onSurface = MaterialColor("onSurface")
    val surfaceVariant = MaterialColor("surfaceVariant")
    val onSurfaceVariant = MaterialColor("onSurfaceVariant")
    val surfaceTint = MaterialColor("surfaceTint")
    val inverseSurface = MaterialColor("inverseSurface")
    val inverseOnSurface = MaterialColor("inverseOnSurface")
    val error = MaterialColor("error")
    val onError = MaterialColor("onError")
    val errorContainer = MaterialColor("errorContainer")
    val onErrorContainer = MaterialColor("onErrorContainer")
    val outline = MaterialColor("outline")
    val outlineVariant = MaterialColor("outlineVariant")
    val scrim = MaterialColor("scrim")
    val surfaceBright = MaterialColor("surfaceBright")
    val surfaceDim = MaterialColor("surfaceDim")
    val surfaceContainer = MaterialColor("surfaceContainer")
    val surfaceContainerHigh = MaterialColor("surfaceContainerHigh")
    val surfaceContainerHighest = MaterialColor("surfaceContainerHighest")
    val surfaceContainerLow = MaterialColor("surfaceContainerLow")
    val surfaceContainerLowest = MaterialColor("surfaceContainerLowest")


}
