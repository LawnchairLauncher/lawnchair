package app.lawnchair.search.algorithms.data

import app.lawnchair.search.algorithms.data.calculator.Expressions

data class Calculation(
    val equation: String,
    val result: String,
    val isValid: Boolean,
)

fun calculateEquationFromString(
    query: String,
): Calculation {
    return try {
        val result = Expressions()
            .eval(query)
            .toString()

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
