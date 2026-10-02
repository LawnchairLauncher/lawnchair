package app.lawnchair.theme.color.tokens

import android.content.Context
import androidx.core.graphics.luminance
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.UiColorMode
import dev.kdrag0n.monet.theme.ColorScheme

/** Shared colors for Personal/Work tabs in the app drawer. */
object AllAppsTabColors {

    fun selectedBackground(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val prefs2 = PreferenceManager2.getInstance(context)
        val entry = prefs2.workProfileTabBackgroundColor.firstCached().colorPreferenceEntry
        // lightColor is the sentinel for "no custom colour" (0); darkColor is always non-zero
        // because it falls back to lightenColor(), so the default check must use lightColor.
        val customColor = entry.lightColor.invoke(context)
        return if (customColor != 0) {
            if (uiColorMode.isDarkTheme) entry.darkColor.invoke(context) else customColor
        } else {
            ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode)
        }
    }

    /**
     * Text color for the selected tab, chosen for contrast against [selectedBackground].
     */
    fun selectedText(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val background = selectedBackground(context, scheme, uiColorMode)
        return if (background.luminance > 0.5f) {
            ColorTokens.Neutral1_900.resolveColor(context, scheme, uiColorMode)
        } else {
            ColorTokens.Neutral1_50.resolveColor(context, scheme, uiColorMode)
        }
    }
}
