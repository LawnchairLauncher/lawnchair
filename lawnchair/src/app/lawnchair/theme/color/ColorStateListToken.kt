package app.lawnchair.theme.color

import android.content.Context
import android.content.res.ColorStateList
import app.lawnchair.theme.ResourceToken
import app.lawnchair.theme.UiColorMode
import dev.kdrag0n.monet.theme.ColorScheme

sealed interface ColorStateListToken : ResourceToken<ColorStateList>

data class NewColorStateList(
    private val factory: (context: Context, scheme: ColorScheme, uiColorMode: UiColorMode) -> ColorStateList
) : ColorStateListToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): ColorStateList {
        return factory(context, scheme, uiColorMode)
    }
}

class DayNightColorStateList(
    private val lightToken: ColorStateListToken,
    private val darkToken: ColorStateListToken
) : ColorStateListToken {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): ColorStateList {
        return if (uiColorMode.isDarkTheme) {
            darkToken.resolve(context, scheme, uiColorMode)
        } else {
            lightToken.resolve(context, scheme, uiColorMode)
        }
    }
}
