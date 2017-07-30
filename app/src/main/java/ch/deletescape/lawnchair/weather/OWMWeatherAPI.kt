package ch.deletescape.lawnchair.weather

import com.kwabenaberko.openweathermaplib.implementation.OpenWeatherMapHelper
import com.kwabenaberko.openweathermaplib.models.CurrentWeather

class OWMWeatherAPI(appId: String) : WeatherAPI(), OpenWeatherMapHelper.CurrentWeatherCallback {

    private val helper: OpenWeatherMapHelper = OpenWeatherMapHelper()

    override var city: String = ""
    override var units: Units = Units.METRIC
        get() = field
        set(value) {
            field = value
            helper.setUnits(value.longName)
        }

    init {
        helper.setAppId(appId)
    }

    override fun getCurrentWeather() {
        helper.getCurrentWeatherByCityName(city, this)
    }

    override fun onSuccess(currentWeather: CurrentWeather) {
        onWeatherData(WeatherData(
                success = true,
                temp = currentWeather.main.temp.toInt(),
                icon = currentWeather.weatherArray[0].icon,
                units = units
        ))
    }

    override fun onFailure(p0: Throwable?) {
        onWeatherData(WeatherData(
                success = false,
                icon = "-1",
                units = units
        ))
    }

    companion object {

        fun create(appId: String) = OWMWeatherAPI(appId)
    }
}