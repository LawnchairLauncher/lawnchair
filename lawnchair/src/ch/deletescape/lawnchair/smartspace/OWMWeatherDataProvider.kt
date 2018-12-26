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

package ch.deletescape.lawnchair.smartspace

import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.util.Temperature
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
            val weather = LawnchairSmartspaceController.WeatherData(iconProvider.getIcon(icon),
                    Temperature(temp!!.roundToInt(), when (owm.unit) {
                        OWM.Unit.METRIC -> Temperature.Unit.Celsius
                        OWM.Unit.IMPERIAL -> Temperature.Unit.Fahrenheit
                        OWM.Unit.STANDARD -> Temperature.Unit.Kelvin
                    }), forecastUrl)
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
                owm.unit = when (prefs.weatherUnit) {
                    Temperature.Unit.Celsius -> OWM.Unit.METRIC
                    Temperature.Unit.Fahrenheit -> OWM.Unit.IMPERIAL
                    else -> OWM.Unit.STANDARD
                }
            } else if (key == "pref_weatherApiKey") {
                owm.apiKey = prefs.weatherApiKey
            }
            if (!force) handler.postAtFrontOfQueue(::updateData)
        }
    }
}
