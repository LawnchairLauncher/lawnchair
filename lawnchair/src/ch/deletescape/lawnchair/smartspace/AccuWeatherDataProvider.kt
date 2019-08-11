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

import android.content.Context
import android.graphics.Bitmap
import android.location.Criteria
import android.location.LocationManager
import android.support.annotation.Keep
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.checkLocationAccess
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.locale
import ch.deletescape.lawnchair.smartspace.accu.AccuRetrofitServiceFactory
import ch.deletescape.lawnchair.smartspace.accu.model.AccuLocalWeatherGSon
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuLocationGSon
import ch.deletescape.lawnchair.smartspace.weather.icons.WeatherIconManager
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.roundToInt

@Keep
class AccuWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)

    private val locationAccess get() = context.checkLocationAccess()
    private val locationManager: LocationManager? by lazy {
        if (locationAccess) {
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        } else null
    }

    private var keyCache = Pair("", "")

    init {
        prefs.addOnPreferenceChangeListener(this, "pref_weather_city")
    }

    override fun updateData() {
        // TODO: Add support for location based info with AccuWeather
        if (false && prefs.weatherCity == "##Auto") {
            if (!locationAccess) {
                Utilities.requestLocationPermission(context.lawnchairApp.activityHandler.foregroundActivity)
                return
            }
            val locationProvider = locationManager?.getBestProvider(Criteria(), true)
            val location = locationManager?.getLastKnownLocation(locationProvider)
            if (location != null) {
            }
        } else {
            if (keyCache.first != prefs.weatherCity) {
                AccuRetrofitServiceFactory.accuSearchRetrofitService.search(prefs.weatherCity, context.locale.language).enqueue(object : Callback<List<AccuLocationGSon>> {
                    override fun onFailure(call: Call<List<AccuLocationGSon>>, t: Throwable) {
                        updateData(null, null)
                    }

                    override fun onResponse(call: Call<List<AccuLocationGSon>>, response: Response<List<AccuLocationGSon>>) {
                        response.body()?.firstOrNull()?.key?.let {
                            keyCache = Pair(prefs.weatherCity, it)
                            loadWeather()
                        }
                    }
                })
            } else {
                loadWeather()
            }
        }
    }

    private fun loadWeather() {
        AccuRetrofitServiceFactory.accuWeatherRetrofitService.getLocalWeather(keyCache.second, context.locale.language).enqueue(object : Callback<AccuLocalWeatherGSon> {
            override fun onFailure(call: Call<AccuLocalWeatherGSon>, t: Throwable) {
                updateData(null, null)
            }

            override fun onResponse(call: Call<AccuLocalWeatherGSon>, response: Response<AccuLocalWeatherGSon>) {
                val conditions = response.body()?.currentConditions
                if (conditions != null) {
                    updateData(LawnchairSmartspaceController.WeatherData(
                            getIcon(context, conditions.weatherIcon, conditions.isDayTime),
                            Temperature(conditions.temperature.value.toFloat().roundToInt(), Temperature.Unit.Celsius),
                            // TODO add support for intents to open the AccuWeather app if available
                            forecastUrl = conditions.mobileLink
                    ), null)
                }
            }

        })
    }

    override fun stopListening() {
        super.stopListening()
        prefs.removeOnPreferenceChangeListener(this, "pref_weather_city")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key == "pref_weather_city" && !force) {
            updateNow()
        }
    }

    companion object {
        // reference: http://apidev.accuweather.com/developers/weatherIcons
        private val ID_MAP = mapOf(
                1 to WeatherIconManager.Icon.CLEAR,
                2 to WeatherIconManager.Icon.MOSTLY_CLEAR,
                3 to WeatherIconManager.Icon.PARTLY_CLOUDY,
                4 to WeatherIconManager.Icon.INTERMITTENT_CLOUDS,
                5 to WeatherIconManager.Icon.HAZY,
                6 to WeatherIconManager.Icon.MOSTLY_CLOUDY,
                7 to WeatherIconManager.Icon.CLOUDY,
                8 to WeatherIconManager.Icon.OVERCAST,
                11 to WeatherIconManager.Icon.FOG,
                12 to WeatherIconManager.Icon.SHOWERS,
                13 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SHOWERS,
                14 to WeatherIconManager.Icon.PARTLY_CLOUDY_W_SHOWERS,
                15 to WeatherIconManager.Icon.THUNDERSTORMS,
                16 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_THUNDERSTORMS,
                17 to WeatherIconManager.Icon.PARTLY_CLOUDY_W_THUNDERSTORMS,
                18 to WeatherIconManager.Icon.RAIN,
                19 to WeatherIconManager.Icon.FLURRIES,
                20 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_FLURRIES,
                21 to WeatherIconManager.Icon.PARTLY_CLOUDY_W_FLURRIES,
                22 to WeatherIconManager.Icon.SNOW,
                23 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SNOW,
                24 to WeatherIconManager.Icon.ICE,
                25 to WeatherIconManager.Icon.SLEET,
                26 to WeatherIconManager.Icon.FREEZING_RAIN,
                29 to WeatherIconManager.Icon.RAIN_AND_SNOW,
                32 to WeatherIconManager.Icon.WINDY,
                33 to WeatherIconManager.Icon.CLEAR,
                34 to WeatherIconManager.Icon.MOSTLY_CLEAR,
                35 to WeatherIconManager.Icon.PARTLY_CLOUDY,
                36 to WeatherIconManager.Icon.INTERMITTENT_CLOUDS,
                37 to WeatherIconManager.Icon.HAZY,
                38 to WeatherIconManager.Icon.MOSTLY_CLOUDY,
                39 to WeatherIconManager.Icon.PARTLY_CLOUDY_W_SHOWERS,
                40 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SHOWERS,
                41 to WeatherIconManager.Icon.PARTLY_CLOUDY_W_THUNDERSTORMS,
                42 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_THUNDERSTORMS,
                43 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_FLURRIES,
                44 to WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SNOW,
                99 to WeatherIconManager.Icon.NA
        )

        fun getIcon(context: Context, iconID: Int, isDay: Boolean): Bitmap {
            return WeatherIconManager.getInstance(context)
                    .getIcon(ID_MAP[iconID] ?: WeatherIconManager.Icon.NA, !isDay)
        }
    }
}