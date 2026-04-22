package app.lawnchair.theme.color

import androidx.annotation.ColorInt
import com.android.systemui.monet.ColorScheme as MonetColorScheme
import com.android.systemui.monet.Style
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch

class MonetColorSchemeCompat(
    @ColorInt private val seedColor: Int,
    val style: Style = Style.TONAL_SPOT,
) : ColorScheme() {

    private val scheme = MonetColorScheme(
        seedColor,
        style,
    )

    override val neutral1: ColorSwatch = mapColors(scheme.neutral1.allShades)
    override val neutral2: ColorSwatch = mapColors(scheme.neutral2.allShades)

    override val accent1: ColorSwatch = mapColors(scheme.accent1.allShades)
    override val accent2: ColorSwatch = mapColors(scheme.accent2.allShades)
    override val accent3: ColorSwatch = mapColors(scheme.accent3.allShades)

    private fun mapColors(colors: List<Int>): Map<Int, Color> {
        val paletteSize = colors.size
        val colorMap = mutableMapOf<Int, Color>()
        colors.forEachIndexed { index, color ->
            val brightness = when (val luminosity = index % paletteSize) {
                0 -> 10
                1 -> 50
                else -> (luminosity - 1) * 100
            }
            colorMap[brightness] = AndroidColor(color)
        }
        colorMap[0] = AndroidColor(android.graphics.Color.WHITE)
        return colorMap
    }
}
