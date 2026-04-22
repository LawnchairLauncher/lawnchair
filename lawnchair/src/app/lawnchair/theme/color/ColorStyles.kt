package app.lawnchair.theme.color

import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.systemui.monet.Style

sealed class ColorStyle(
    val style: Style,
    @StringRes val nameResourceId: Int,
) {
    companion object {
        fun fromString(value: String): ColorStyle = when (value) {
            "spritz" -> Spritz
            "vibrant" -> Vibrant
            "expressive" -> Expressive
            "rainbow" -> Rainbow
            "fruit_salad" -> FruitSalad
            "content" -> Content
            "monochromatic" -> Monochromatic
            else -> TonalSpot // TonalSpot is the default scheme
        }

        /**
         * @return The list of all color styles modes.
         */
        fun values() = listOf(
            Spritz,
            TonalSpot,
            Vibrant,
            Expressive,
            Rainbow,
            FruitSalad,
            Content,
            Monochromatic,
        )
    }
}

object Spritz : ColorStyle(Style.SPRITZ, R.string.color_style_spritz) {
    override fun toString() = "spritz"
}
object TonalSpot : ColorStyle(Style.TONAL_SPOT, R.string.color_style_tonal_spot) {
    override fun toString() = "tonal_spot"
}
object Vibrant : ColorStyle(Style.VIBRANT, R.string.color_style_vibrant) {
    override fun toString() = "vibrant"
}
object Expressive : ColorStyle(Style.EXPRESSIVE, R.string.color_style_expressive) {
    override fun toString() = "expressive"
}
object Rainbow : ColorStyle(Style.RAINBOW, R.string.color_style_rainbow) {
    override fun toString() = "rainbow"
}
object FruitSalad : ColorStyle(Style.FRUIT_SALAD, R.string.color_style_fruit_salad) {
    override fun toString() = "fruit_salad"
}
object Content : ColorStyle(Style.CONTENT, R.string.color_style_content) {
    override fun toString() = "content"
}
object Monochromatic : ColorStyle(Style.MONOCHROMATIC, R.string.color_style_monochromatic) {
    override fun toString() = "monochromatic"
}
