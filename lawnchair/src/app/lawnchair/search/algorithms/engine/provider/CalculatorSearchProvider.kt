package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.algorithms.data.Calculation
import app.lawnchair.search.algorithms.data.calculator.Expressions
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import java.math.BigDecimal
import java.math.MathContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object CalculatorSearchProvider : SearchProvider {

    override val id: String = "calculator"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val legacyPrefs = PreferenceManager.getInstance(context)

        if (query.isBlank() || !legacyPrefs.searchResultCalculator.get()) {
            emit(emptyList())
            return@flow
        }

        val calculation = calculateEquationFromString(query)

        if (calculation.isValid) {
            val searchResult = SearchResult.Calculation(data = calculation)
            emit(listOf(searchResult))
        } else {
            emit(emptyList())
        }
    }

    private fun calculateEquationFromString(
        query: String,
    ): Calculation {
        return try {
            val evaluatedValue = Expressions().eval(query)
            val roundedValue = evaluatedValue.round(MathContext.DECIMAL64)
            val formattedValue = roundedValue.stripTrailingZeros()
            val absoluteValue = formattedValue.abs()
            val threshold = BigDecimal("9999999999999999")

            val result = if (absoluteValue > threshold) {
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
}
