/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.util

import kotlin.math.roundToInt

class Temperature(val value: Int, val unit: Unit) {

    fun inUnit(other: Unit): Int {
        if (other == unit) return value
        return ((value.toFloat() - unit.freezingPoint) / unit.range * other.range + other.freezingPoint).roundToInt()
    }

    enum class Unit(val freezingPoint: Float, boilingPoint: Float, val suffix: String) {

        Celsius(0f, 100f, "°C"),
        Fahrenheit(32f, 212f, "°F"),
        Kelvin(273f, 373f, "K");

        val range = boilingPoint - freezingPoint
    }

    companion object {

        fun unitFromString(unit: String): Unit {
            return when (unit) {
                "metric" -> Unit.Celsius
                "imperial" -> Unit.Fahrenheit
                "kelvin" -> Unit.Kelvin
                else -> throw IllegalArgumentException("unknown unit $unit")
            }
        }

        fun unitToString(unit: Unit): String {
            return when (unit) {
                Unit.Celsius -> "metric"
                Unit.Fahrenheit -> "imperial"
                Unit.Kelvin -> "kelvin"
            }
        }
    }
}
