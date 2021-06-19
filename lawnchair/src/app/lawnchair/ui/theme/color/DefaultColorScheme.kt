package app.lawnchair.ui.theme.color

import android.content.Context
import com.android.launcher3.R
import dev.kdrag0n.android12ext.monet.colors.Srgb
import dev.kdrag0n.android12ext.monet.theme.ColorScheme
import dev.kdrag0n.android12ext.monet.theme.DynamicColorScheme

class DefaultColorScheme(
    context: Context,
    targetColors: ColorScheme,
) : DynamicColorScheme(targetColors, context.getColor(R.color.primary_500)) {

    override val primaryDark = Srgb(context.getColor(R.color.primary_200))
}
