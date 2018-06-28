package ch.deletescape.lawnchair.weather

import android.content.Context
import ch.deletescape.lawnchair.R
import java.util.*

abstract class WeatherAPI {

    abstract var units: Units
    abstract var city: String

    var weatherCallback: WeatherCallback? = null

    abstract fun getCurrentWeather()
    abstract fun getForecastURL():String

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
        fun create(context: Context, provider: Int): WeatherAPI {
            try {
                val providers = context.resources.getStringArray(R.array.weatherProviderClasses)
                val providerClass = context.classLoader.loadClass(providers[provider])
                val constructor = providerClass.getConstructor(Context::class.java)
                return constructor.newInstance(context) as WeatherAPI
            } catch (ignored: ClassNotFoundException) {
                throw RuntimeException("Provider $provider not found")
            }
        }
    }
}