package app.lawnchair.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

fun Float.round(decimals: Int): Float {
    val symbols = DecimalFormatSymbols()
    symbols.decimalSeparator = '.'
    val format = DecimalFormat()
    format.decimalFormatSymbols = symbols
    format.applyPattern(
        "#.${"#".repeat(decimals)}"
    )
    return format.format(this).toFloat()
}