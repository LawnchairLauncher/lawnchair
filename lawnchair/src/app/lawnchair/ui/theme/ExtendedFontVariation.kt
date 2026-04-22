package app.lawnchair.ui.theme

import androidx.compose.ui.text.font.FontVariation

object ExtendedFontVariation {
    /**
     * Typographic feature axis for (ROND) variations
     *
     * [OpenType Variable Axes Definition](https://fonts.google.com/variablefonts#axis-definitions)
     *
     * @param value Round axis, in 0..100
     **/
    fun round(value: Int): FontVariation.Setting {
        val featureTagType = "ROND"

        require(value in 0..100) { "Google Sans Flex 'Round' axis must be in 0..100" }
        return FontVariation.Setting(featureTagType, value.toFloat())
    }
}
