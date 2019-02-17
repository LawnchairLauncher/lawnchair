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

import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Utilities
import net.aksingh.owmjapis.core.OWM
import kotlin.math.roundToInt

@Keep
class OWMWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller), LawnchairPreferences.OnPreferenceChangeListener {

    private val context = controller.context
    private val prefs = Utilities.getLawnchairPrefs(context)
    private val owm = OWM(prefs.weatherApiKey)
    private val iconProvider = WeatherIconProvider(context)

    init {
        prefs.addOnPreferenceChangeListener(this, "pref_weatherApiKey", "pref_weather_city", "pref_weather_units")
    }

    override fun queryWeatherData(): LawnchairSmartspaceController.WeatherData? {
        try {
            val currentWeatherList = owm.currentWeatherByCityName(prefs.weatherCity)
            val temp = if (currentWeatherList.hasMainData()) currentWeatherList.mainData!!.temp else -1.0
            val icon = if (currentWeatherList.hasMainData()) currentWeatherList.weatherList?.get(0)?.iconCode else "-1"
            val forecastUrl = "https://openweathermap.org/city/${currentWeatherList.cityId}"
            return LawnchairSmartspaceController.WeatherData(iconProvider.getIcon(icon),
                    Temperature(temp!!.roundToInt(), when (owm.unit) {
                        OWM.Unit.METRIC -> Temperature.Unit.Celsius
                        OWM.Unit.IMPERIAL -> Temperature.Unit.Fahrenheit
                        OWM.Unit.STANDARD -> Temperature.Unit.Kelvin
                    }), forecastUrl)
        } catch (e: Exception){
            Log.w("OWM", "Updating weather data failed", e)
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
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
            if (!force) updateNow()
        }
    }
}
