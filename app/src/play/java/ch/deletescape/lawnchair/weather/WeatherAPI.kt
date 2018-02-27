package ch.deletescape.lawnchair.weather

import android.content.Context
import java.util.*

abstract class WeatherAPI {

    abstract var units: Units
    abstract var city: String

    var weatherCallback: WeatherCallback? = null

    abstract fun getCurrentWeather()

    fun onWeatherData(data: WeatherData) {
        weatherCallback?.onWeatherData(data)
    }

    interface WeatherCallback {

        fun onWeatherData(data: WeatherData)
    }

    enum class Units {
        IMPERIAL {
            override val shortName = "F"
            override val longName = "imperial"
        },
        METRIC {
            override val shortName = "C"
            override val longName = "metric"
        };

        abstract val shortName: String
        abstract val longName: String
    }

    data class WeatherData(
            val success: Boolean,
            val temp: Int = -1,
            val icon: String = "-1",
            val units: Units) {

        val temperatureString: String
            get() = if (success) String.format(Locale.US, "%d°%s", temp, units.shortName)
                    else "ERROR"
    }

    companion object {
        const val PROVIDER_OPENWEATHERMAP = 0
        const val PROVIDER_GOOGLE_AWARENESS = 1

        fun create(context: Context, provider: Int) = when (provider) {
            PROVIDER_OPENWEATHERMAP -> OWMWeatherAPI(context)
            PROVIDER_GOOGLE_AWARENESS -> AwarenessWeatherAPI(context)
            else -> throw IllegalArgumentException("Provider must be either PROVIDER_OPENWEATHERMAP or PROVIDER_GOOGLE_AWARENESS")
        }
    }
}