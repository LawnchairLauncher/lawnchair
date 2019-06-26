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
import android.graphics.BitmapFactory
import android.location.Criteria
import android.location.LocationManager
import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.checkLocationAccess
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.locale
import ch.deletescape.lawnchair.smartspace.accu.AccuRetrofitServiceFactory
import ch.deletescape.lawnchair.smartspace.accu.model.AccuLocalWeatherGSon
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuLocationGSon
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.R
import com.android.launcher3.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.roundToInt

@Keep
class AccuWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller), LawnchairPreferences.OnPreferenceChangeListener {

    private val context = controller.context
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
                            getIcon(context, conditions.weatherIcon),
                            Temperature(conditions.temperature.value.toFloat().roundToInt(), Temperature.Unit.Celsius),
                            // TODO add support for intents to open the AccuWeather app if available
                            forecastUrl = conditions.mobileLink
                    ), null)
                }
            }

        })
    }

    override fun onDestroy() {
        super.onDestroy()
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
                1 to R.drawable.weather_01,
                2 to R.drawable.weather_01,
                3 to R.drawable.weather_01,
                4 to R.drawable.weather_01,
                5 to R.drawable.weather_02,
                6 to R.drawable.weather_02,
                7 to R.drawable.weather_03,
                8 to R.drawable.weather_03,
                11 to R.drawable.weather_50,
                12 to R.drawable.weather_09,
                13 to R.drawable.weather_10,
                14 to R.drawable.weather_10,
                15 to R.drawable.weather_11,
                16 to R.drawable.weather_11,
                17 to R.drawable.weather_11,
                18 to R.drawable.weather_09,
                19 to R.drawable.weather_13,
                20 to R.drawable.weather_13,
                21 to R.drawable.weather_13,
                22 to R.drawable.weather_13,
                23 to R.drawable.weather_13,
                24 to R.drawable.weather_10,
                25 to R.drawable.weather_13,
                26 to R.drawable.weather_10,
                29 to R.drawable.weather_13,
                33 to R.drawable.weather_01n,
                34 to R.drawable.weather_01n,
                35 to R.drawable.weather_02n,
                36 to R.drawable.weather_02n,
                37 to R.drawable.weather_03n,
                38 to R.drawable.weather_03n,
                39 to R.drawable.weather_10n,
                40 to R.drawable.weather_09,
                41 to R.drawable.weather_11,
                42 to R.drawable.weather_11,
                43 to R.drawable.weather_13,
                44 to R.drawable.weather_13,
                99 to R.drawable.weather_none_available
        )

        const val CONDITION_UNKNOWN = 99

        fun getIcon(context: Context, iconID: Int): Bitmap {
            var resID = iconID
            if (!ID_MAP.containsKey(resID)) {
                Log.e("WeatherIconProvider", "No weather icon exists for condition: $resID")
                resID = CONDITION_UNKNOWN
            }

            return BitmapFactory.decodeResource(context.resources, ID_MAP[resID]!!)
        }
    }
}