package app.lawnchair.theme.color

import android.content.res.ColorStateList

object ColorStateListTokens {

    val AllAppsTabTextLight = NewColorStateList { context, scheme, uiColorMode ->
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        )
        val colors = intArrayOf(
            ColorTokens.TextColorPrimaryInverse.resolveColor(context, scheme, uiColorMode),
            ColorTokens.TextColorSecondary.resolveColor(context, scheme, uiColorMode),
        )
        ColorStateList(states, colors)
    }

    val AllAppsTabTextDark = NewColorStateList { context, scheme, uiColorMode ->
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        )
        val colors = intArrayOf(
            ColorTokens.TextColorPrimaryInverse.resolveColor(context, scheme, uiColorMode),
            ColorTokens.TextColorSecondary.resolveColor(context, scheme, uiColorMode),
        )
        ColorStateList(states, colors)
    }

    @JvmField val AllAppsTabText = DayNightColorStateList(AllAppsTabTextLight, AllAppsTabTextDark)
}
