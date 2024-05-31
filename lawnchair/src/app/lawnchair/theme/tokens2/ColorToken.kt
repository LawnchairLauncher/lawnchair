package app.lawnchair.theme.tokens2

import android.content.Context
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.colorscheme.LightDarkScheme
import com.android.launcher3.R
import com.android.launcher3.util.Themes

interface ColorToken<T> {
    fun resolveColor(context: Context): T = resolveColor(context, UiColorMode(Themes.getAttrInteger(context, R.attr.uiColorMode)))
    fun resolveColor(context: Context, uiColorMode: UiColorMode): T {
        val themeProvider = ThemeProvider.INSTANCE.get(context)
        return resolveColor(context, themeProvider.colorScheme, uiColorMode)
    }
    fun resolveColor(context: Context, scheme: LightDarkScheme, uiColorMode: UiColorMode): T
}


class MaterialColor(
    val color: String
) : ColorToken<Int> {
    override fun resolveColor(context: Context, scheme: LightDarkScheme, uiColorMode: UiColorMode): Int {
        return if (uiColorMode.isDarkTheme) {
            scheme.darkScheme.get(color)
        } else {
            scheme.lightScheme.get(color)
        }
    }
}

object Material3Colors {
    @JvmField val primary = MaterialColor("primary")
    @JvmField val onPrimary = MaterialColor("onPrimary")
    @JvmField val primaryContainer = MaterialColor("primaryContainer")
    @JvmField val onPrimaryContainer = MaterialColor("onPrimaryContainer")
    @JvmField val inversePrimary = MaterialColor("inversePrimary")
    @JvmField val secondary = MaterialColor("secondary")
    @JvmField val onSecondary = MaterialColor("onSecondary")
    @JvmField val secondaryContainer = MaterialColor("secondaryContainer")
    @JvmField val onSecondaryContainer = MaterialColor("onSecondaryContainer")
    @JvmField val tertiary = MaterialColor("tertiary")
    @JvmField val onTertiary = MaterialColor("onTertiary")
    @JvmField val tertiaryContainer = MaterialColor("tertiaryContainer")
    @JvmField val onTertiaryContainer = MaterialColor("onTertiaryContainer")
    @JvmField val background = MaterialColor("background")
    @JvmField val onBackground = MaterialColor("onBackground")
    @JvmField val surface = MaterialColor("surface")
    @JvmField val onSurface = MaterialColor("onSurface")
    @JvmField val surfaceVariant = MaterialColor("surfaceVariant")
    @JvmField val onSurfaceVariant = MaterialColor("onSurfaceVariant")
    @JvmField val surfaceTint = MaterialColor("surfaceTint")
    @JvmField val inverseSurface = MaterialColor("inverseSurface")
    @JvmField val inverseOnSurface = MaterialColor("inverseOnSurface")
    @JvmField val error = MaterialColor("error")
    @JvmField val onError = MaterialColor("onError")
    @JvmField val errorContainer = MaterialColor("errorContainer")
    @JvmField val onErrorContainer = MaterialColor("onErrorContainer")
    @JvmField val outline = MaterialColor("outline")
    @JvmField val outlineVariant = MaterialColor("outlineVariant")
    @JvmField val scrim = MaterialColor("scrim")
    @JvmField val surfaceBright = MaterialColor("surfaceBright")
    @JvmField val surfaceDim = MaterialColor("surfaceDim")
    @JvmField val surfaceContainer = MaterialColor("surfaceContainer")
    @JvmField val surfaceContainerHigh = MaterialColor("surfaceContainerHigh")
    @JvmField val surfaceContainerHighest = MaterialColor("surfaceContainerHighest")
    @JvmField val surfaceContainerLow = MaterialColor("surfaceContainerLow")
    @JvmField val surfaceContainerLowest = MaterialColor("surfaceContainerLowest")

    @JvmField val WorkspaceAccentColor = primaryContainer
    @JvmField val AllAppsBackground = surface
    @JvmField val AllAppsHeaderProtection = surfaceContainerHigh
    @JvmField val AllAppsBottomSheetBackground = surfaceDim
}
