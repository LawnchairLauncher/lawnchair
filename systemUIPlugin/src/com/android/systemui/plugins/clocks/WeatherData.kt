package com.android.systemui.plugins.clocks

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting

typealias WeatherTouchAction = (View) -> Unit

data class WeatherData(
    val description: String,
    val state: WeatherStateIcon,
    val useCelsius: Boolean,
    val temperature: Int,
    val touchAction: WeatherTouchAction? = null,
) {
    companion object {
        const val DEBUG = true
        private const val TAG = "WeatherData"
        @VisibleForTesting const val DESCRIPTION_KEY = "description"
        @VisibleForTesting const val STATE_KEY = "state"
        @VisibleForTesting const val USE_CELSIUS_KEY = "use_celsius"
        @VisibleForTesting const val TEMPERATURE_KEY = "temperature"
        private const val INVALID_WEATHER_ICON_STATE = -1

        fun fromBundle(extras: Bundle, touchAction: WeatherTouchAction? = null): WeatherData? {
            val description = extras.getString(DESCRIPTION_KEY)
            val state =
                WeatherStateIcon.fromInt(extras.getInt(STATE_KEY, INVALID_WEATHER_ICON_STATE))
            val temperature = readIntFromBundle(extras, TEMPERATURE_KEY)
            if (
                description == null ||
                    state == null ||
                    !extras.containsKey(USE_CELSIUS_KEY) ||
                    temperature == null
            ) {
                if (DEBUG) {
                    Log.w(TAG, "Weather data did not parse from $extras")
                }
                return null
            } else {
                val result =
                    WeatherData(
                        description = description,
                        state = state,
                        useCelsius = extras.getBoolean(USE_CELSIUS_KEY),
                        temperature = temperature,
                        touchAction = touchAction
                    )
                if (DEBUG) {
                    Log.i(TAG, "Weather data parsed $result from $extras")
                }
                return result
            }
        }

        private fun readIntFromBundle(extras: Bundle, key: String): Int? =
            try {
                extras.getString(key)?.toInt()
            } catch (e: Exception) {
                null
            }
    }

    // Values for WeatherStateIcon must stay in sync with go/g3-WeatherStateIcon
    enum class WeatherStateIcon(val id: Int) {
        UNKNOWN_ICON(0),

        // Clear, day & night.
        SUNNY(1),
        CLEAR_NIGHT(2),

        // Mostly clear, day & night.
        MOSTLY_SUNNY(3),
        MOSTLY_CLEAR_NIGHT(4),

        // Partly cloudy, day & night.
        PARTLY_CLOUDY(5),
        PARTLY_CLOUDY_NIGHT(6),

        // Mostly cloudy, day & night.
        MOSTLY_CLOUDY_DAY(7),
        MOSTLY_CLOUDY_NIGHT(8),
        CLOUDY(9),
        HAZE_FOG_DUST_SMOKE(10),
        DRIZZLE(11),
        HEAVY_RAIN(12),
        SHOWERS_RAIN(13),

        // Scattered showers, day & night.
        SCATTERED_SHOWERS_DAY(14),
        SCATTERED_SHOWERS_NIGHT(15),

        // Isolated scattered thunderstorms, day & night.
        ISOLATED_SCATTERED_TSTORMS_DAY(16),
        ISOLATED_SCATTERED_TSTORMS_NIGHT(17),
        STRONG_TSTORMS(18),
        BLIZZARD(19),
        BLOWING_SNOW(20),
        FLURRIES(21),
        HEAVY_SNOW(22),

        // Scattered snow showers, day & night.
        SCATTERED_SNOW_SHOWERS_DAY(23),
        SCATTERED_SNOW_SHOWERS_NIGHT(24),
        SNOW_SHOWERS_SNOW(25),
        MIXED_RAIN_HAIL_RAIN_SLEET(26),
        SLEET_HAIL(27),
        TORNADO(28),
        TROPICAL_STORM_HURRICANE(29),
        WINDY_BREEZY(30),
        WINTRY_MIX_RAIN_SNOW(31);

        companion object {
            fun fromInt(value: Int) = values().firstOrNull { it.id == value }
        }
    }

    override fun toString(): String {
        val unit = if (useCelsius) "C" else "F"
        return "$state (\"$description\") $temperature°$unit"
    }
}
