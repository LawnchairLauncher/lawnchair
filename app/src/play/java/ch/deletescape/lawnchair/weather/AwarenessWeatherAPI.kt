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
            val icon = getWeatherIcon(weatherResult.weather.conditions)
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

    private fun getWeatherIcon(condition: IntArray): String {
        val conditions = condition.fold(0) { acc, i -> acc or CONDITIONS[i] }
        if (conditions and CONDITION_STORMY != 0)
            return WeatherIconProvider.CONDITION_STORM
        if (conditions and CONDITION_SNOWY != 0 || conditions and CONDITION_ICY != 0)
            return WeatherIconProvider.CONDITION_SNOW
        if (conditions and CONDITION_RAINY != 0)
            return WeatherIconProvider.CONDITION_SHOWERS
        if (conditions and CONDITION_FOGGY != 0 || conditions and CONDITION_HAZY != 0)
            return WeatherIconProvider.CONDITION_MIST
        if (conditions and CONDITION_CLOUDY != 0 && conditions and CONDITION_CLEAR != 0)
            return WeatherIconProvider.CONDITION_CLOUDS
        if (conditions and CONDITION_CLOUDY != 0)
            return WeatherIconProvider.CONDITION_MOST_CLOUDS
        if (conditions and CONDITION_CLEAR != 0)
            return WeatherIconProvider.CONDITION_FEW_CLOUDS
        return WeatherIconProvider.CONDITION_UNKNOWN
    }

    companion object {
        private const val CONDITION_UNKNOWN = 1 shl 0
        private const val CONDITION_CLEAR = 1 shl 1
        private const val CONDITION_CLOUDY = 1 shl 2
        private const val CONDITION_FOGGY = 1 shl 3
        private const val CONDITION_HAZY = 1 shl 4
        private const val CONDITION_ICY = 1 shl 5
        private const val CONDITION_RAINY = 1 shl 6
        private const val CONDITION_SNOWY = 1 shl 7
        private const val CONDITION_STORMY = 1 shl 8
        private const val CONDITION_WINDY = 1 shl 9

        private val CONDITIONS = intArrayOf(
                AwarenessWeatherAPI.CONDITION_UNKNOWN,
                AwarenessWeatherAPI.CONDITION_CLEAR,
                AwarenessWeatherAPI.CONDITION_CLOUDY,
                AwarenessWeatherAPI.CONDITION_FOGGY,
                AwarenessWeatherAPI.CONDITION_HAZY,
                AwarenessWeatherAPI.CONDITION_ICY,
                AwarenessWeatherAPI.CONDITION_RAINY,
                AwarenessWeatherAPI.CONDITION_SNOWY,
                AwarenessWeatherAPI.CONDITION_STORMY,
                AwarenessWeatherAPI.CONDITION_WINDY
        )
    }
}