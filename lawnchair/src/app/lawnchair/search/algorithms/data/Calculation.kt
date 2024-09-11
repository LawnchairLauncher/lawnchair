package app.lawnchair.search.algorithms.data

import app.lawnchair.search.algorithms.data.calculator.Expressions
import java.math.BigDecimal
import java.math.MathContext

data class Calculation(
    val equation: String,
    val result: String,
    val isValid: Boolean,
)

fun calculateEquationFromString(
    query: String,
): Calculation {
    return try {
        val evaluatedValue = Expressions().eval(query)
        val roundedValue = evaluatedValue.round(MathContext.DECIMAL64)
        val formattedValue = roundedValue.stripTrailingZeros()
        val absoluteValue = formattedValue.abs()
        val threshold = BigDecimal("9999999999999999")

        val result = if (absoluteValue.compareTo(threshold) > 0) {
            formattedValue.toString()
        } else {
            formattedValue.toPlainString()
        }

        Calculation(
            equation = query,
            result = result,
            isValid = true,
        )
    } catch (_: Exception) {
        Calculation(
            equation = "",
            result = "",
            isValid = false,
        )
    }
}
