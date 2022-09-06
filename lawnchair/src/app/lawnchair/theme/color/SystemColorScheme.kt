package app.lawnchair.theme.color

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.monet.theme.ColorScheme

class SystemColorScheme(context: Context) : ColorScheme() {
    private val resources = context.resources

    override val neutral1 = systemPaletteMap("neutral1")
    override val neutral2 = systemPaletteMap("neutral2")

    override val accent1 = systemPaletteMap("accent1")
    override val accent2 = systemPaletteMap("accent2")
    override val accent3 = systemPaletteMap("accent3")

    private fun systemPaletteMap(paletteName: String): Map<Int, Color> {
        val lums = listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        return lums.associateWith { loadSystemColor(paletteName, it) }
    }

    private fun loadSystemColor(paletteName: String, lum: Int): AndroidColor {
        val colorName = "system_${paletteName}_$lum"
        val colorId = resources.getIdentifier(colorName, "color", "android")
        return AndroidColor(ResourcesCompat.getColor(resources, colorId, null))
    }
}
