package ch.deletescape.lawnchair.weather

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.awareness.Awareness
import com.google.android.gms.awareness.snapshot.WeatherResult
import com.google.android.gms.awareness.state.Weather
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import java.util.*

class AwarenessWeatherAPI(context: Context) : WeatherAPI(), ResultCallback<WeatherResult> {

    override var units = Units.METRIC
    override var city = ""

    val iconSuffix: String
        get() = if (Calendar.getInstance().apply {
            time = Date()
        }[Calendar.HOUR_OF_DAY] in 7..18) "d" else "n"

    private val googleApiClient: GoogleApiClient = GoogleApiClient.Builder(context)
            .addApi(Awareness.API)
            .build()

    init {
        googleApiClient.connect()
    }

    @SuppressLint("MissingPermission")
    override fun getCurrentWeather() {
        Awareness.SnapshotApi.getWeather(googleApiClient)
                .setResultCallback(this)
    }

    override fun onResult(weatherResult: WeatherResult) {
        if (weatherResult.status.isSuccess) {
            val temp = weatherResult.weather.getTemperature(
                    when (units) {
                        Units.METRIC -> Weather.CELSIUS
                        Units.IMPERIAL -> Weather.FAHRENHEIT
                    }
            )
            val icon = getWeatherIcon(weatherResult.weather.conditions[0])
            //Log.d("AWAPI", "condition: " + Arrays.toString(weatherResult.weather.conditions))
            //val icon = "04d"
            onWeatherData(WeatherData(
                    success = true,
                    temp = temp.toInt(),
                    icon = icon + iconSuffix,
                    units = units
            ))
        } else {
            onWeatherData(WeatherData(
                    success = false,
                    units = units
            ))
        }
    }

    private fun getWeatherIcon(condition: Int): String {
        return when (condition) {
            Weather.CONDITION_CLEAR -> WeatherIconProvider.CONDITION_FEW_CLOUDS
            Weather.CONDITION_CLOUDY -> WeatherIconProvider.CONDITION_MOST_CLOUDS
            Weather.CONDITION_FOGGY -> WeatherIconProvider.CONDITION_MIST
            Weather.CONDITION_HAZY -> WeatherIconProvider.CONDITION_MIST
            Weather.CONDITION_ICY -> WeatherIconProvider.CONDITION_SNOW
            Weather.CONDITION_RAINY -> WeatherIconProvider.CONDITION_SHOWERS
            Weather.CONDITION_SNOWY -> WeatherIconProvider.CONDITION_SNOW
            Weather.CONDITION_STORMY -> WeatherIconProvider.CONDITION_STORM
            Weather.CONDITION_WINDY -> WeatherIconProvider.CONDITION_CLOUDS
            else -> WeatherIconProvider.CONDITION_UNKNOWN
        }
    }
}