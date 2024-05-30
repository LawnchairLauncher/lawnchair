package app.lawnchair.theme

import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import dev.kdrag0n.monet.theme.TonalPalette

interface ResourceToken<T> {
    fun resolve(context: Context): T = resolve(context, UiColorMode(Themes.getAttrInteger(context, R.attr.uiColorMode)))
    fun resolve(context: Context, uiColorMode: UiColorMode): T {
        val themeProvider = ThemeProvider.INSTANCE.get(context)
        return resolve(context, themeProvider.tonalPalette, uiColorMode)
    }
    fun resolve(context: Context, scheme: TonalPalette, uiColorMode: UiColorMode): T
}
