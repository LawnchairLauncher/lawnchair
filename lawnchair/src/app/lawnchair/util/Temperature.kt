package app.lawnchair.util

import kotlin.math.roundToInt

class Temperature(val value: Int, val unit: Unit) {

    fun inUnit(other: Unit): Int {
        if (other == unit) return value
        return ((value.toFloat() - unit.freezingPoint) / unit.range * other.range + other.freezingPoint).roundToInt()
    }

    enum class Unit(val freezingPoint: Float, boilingPoint: Float, val suffix: String) {

        Celsius(0f, 100f, "°C"),
        Fahrenheit(32f, 212f, "°F"),
        Kelvin(273f, 373f, "K"),
        Rakine(491.67f, 671.641f, "°R"),
        Delisle(-100f, 50f, "°D"),
        Newton(0f, 33f, "°N"),
        Reaumur(0f, 80f, "°Ré"),
        Romer(7.5f, 60f, "°Rø");

        val range = boilingPoint - freezingPoint
    }

    companion object {

        fun unitFromString(unit: String): Unit {
            return when (unit) {
                "metric" -> Unit.Celsius
                "imperial" -> Unit.Fahrenheit
                "kelvin" -> Unit.Kelvin
                "rakine" -> Unit.Rakine
                "delisle" -> Unit.Delisle
                "newton" -> Unit.Newton
                "reaumur" -> Unit.Reaumur
                "romer" -> Unit.Romer
                else -> throw IllegalArgumentException("unknown unit $unit")
            }
        }

        fun unitToString(unit: Unit): String {
            return when (unit) {
                Unit.Celsius -> "metric"
                Unit.Fahrenheit -> "imperial"
                Unit.Kelvin -> "kelvin"
                Unit.Rakine -> "rakine"
                Unit.Delisle -> "delisle"
                Unit.Newton -> "newton"
                Unit.Reaumur -> "reaumur"
                Unit.Romer -> "romer"
            }
        }
    }
}
