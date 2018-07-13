package ch.deletescape.lawnchair.smartspace

import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.Utilities
import net.aksingh.owmjapis.core.OWM
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Keep
class OWMWeatherDataProvider(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller), LawnchairPreferences.OnPreferenceChangeListener {

    private val context = controller.context
    private val prefs = Utilities.getLawnchairPrefs(context)
    private val handlerThread = HandlerThread("owm").apply { if (!isAlive) start() }
    private val handler: Handler = Handler(handlerThread.looper)
    private val owm = OWM(prefs.weatherApiKey)
    private val iconProvider = WeatherIconProvider(context)

    init {
        prefs.addOnPreferenceChangeListener(this, "pref_weatherApiKey", "pref_weather_city", "pref_weather_units")
    }

    override fun performSetup() {
        super.performSetup()
        handler.post(::periodicUpdate)
    }

    private fun updateData() {
        try {
            val currentWeatherList = owm.currentWeatherByCityName(prefs.weatherCity)
            val temp = if (currentWeatherList.hasMainData()) currentWeatherList.mainData!!.temp else -1.0
            val icon = if (currentWeatherList.hasMainData()) currentWeatherList.weatherList?.get(0)?.iconCode else "-1"
            val forecastUrl = "https://openweathermap.org/city/${currentWeatherList.cityId}"
            val weather = LawnchairSmartspaceController.WeatherData(iconProvider.getIcon(icon), temp!!.roundToInt(), owm.unit == OWM.Unit.METRIC, forecastUrl)
            super.updateData(weather, null)
        } catch (e:Exception){
            Log.w("OWM", "Updating weather data failed", e)
        }
    }

    private fun periodicUpdate() {
        updateData()
        handler.postDelayed(::periodicUpdate, TimeUnit.MINUTES.toMillis(30))
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerThread.quit()
        prefs.removeOnPreferenceChangeListener(this, "pref_weatherApiKey", "pref_weather_city", "pref_weather_units")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key in arrayOf("pref_weatherApiKey", "pref_weather_city", "pref_weather_units")) {
            if (key == "pref_weather_units") {
                owm.unit = if (prefs.weatherUnit == "metric") {
                    OWM.Unit.METRIC
                } else {
                    OWM.Unit.IMPERIAL
                }
            } else if (key == "pref_weatherApiKey") {
                owm.apiKey = prefs.weatherApiKey
            }
            if (!force) handler.postAtFrontOfQueue(::updateData)
        }
    }
}
