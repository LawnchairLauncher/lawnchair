package app.lawnchair.ui.theme.color

import android.content.Context
import android.content.res.Resources
import dev.kdrag0n.android12ext.monet.colors.Color
import dev.kdrag0n.android12ext.monet.colors.Srgb
import dev.kdrag0n.android12ext.monet.theme.ColorScheme

class SystemColorScheme(private val context: Context) : ColorScheme() {
    override val neutral1 = systemPaletteMap("neutral1")
    override val neutral2 = systemPaletteMap("neutral2")

    override val accent1 = systemPaletteMap("accent1")
    override val accent2 = systemPaletteMap("accent2")
    override val accent3 = systemPaletteMap("accent3")

    private fun systemPaletteMap(paletteName: String): Map<Int, Color> {
        val lums = listOf(0, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        return lums.associateWith { SystemColor(context.resources, paletteName, it) }
    }
}

class SystemColor(resources: Resources, paletteName: String, it: Int) : Color {
    @Suppress("DEPRECATION") // use Resources.getColor(int) directly because we don't need the theme
    private val srgb by lazy {
        val colorName = "system_${paletteName}_$it"
        val colorId = resources.getIdentifier(colorName, "color", "android")
        Srgb(resources.getColor(colorId))
    }

    override fun toLinearSrgb() = srgb.toLinearSrgb()
    override fun toSrgb() = srgb
}
