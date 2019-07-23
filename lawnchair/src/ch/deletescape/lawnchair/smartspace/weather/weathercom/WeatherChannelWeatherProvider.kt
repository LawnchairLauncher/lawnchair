/*
 *     Copyright (c) 2017-2019 the Lawnchair team
 *     Copyright (c)  2019 oldosfan (would)
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

package ch.deletescape.lawnchair.smartspace.weather.weathercom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.locale
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import android.location.Criteria
import android.location.LocationManager
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.PeriodicDataProvider
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider
import ch.deletescape.lawnchair.smartspace.weather.icons.WeatherIconManager
import ch.deletescape.lawnchair.smartspace.weathercom.Constants
import ch.deletescape.lawnchair.smartspace.weathercom.WeatherComRetrofitServiceFactory
import ch.deletescape.lawnchair.util.Temperature
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.Utilities

class WeatherChannelWeatherProvider(controller: LawnchairSmartspaceController) :
        PeriodicDataProvider(controller) {

    private val locationAccess by lazy { context.checkLocationAccess() }

    private val locationManager: LocationManager? by lazy {
        if (locationAccess) {
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        } else null
    }

    @SuppressLint("MissingPermission")
    override fun updateData() {
        runOnUiWorkerThread {
            if (context.lawnchairPrefs.weatherCity != "##Auto") {
                try {
                    val position = WeatherComRetrofitServiceFactory.weatherComWeatherRetrofitService
                            .searchLocationByName(context.lawnchairPrefs.weatherCity, "city",
                                                  context.locale.language).execute()

                    d("updateData: position $position")
                    val currentConditions =
                            WeatherComRetrofitServiceFactory.weatherComWeatherRetrofitService.getCurrentConditions(
                                    position.body()!!.location.latitude[0],
                                    position.body()!!.location.longitude[0]).execute().body()!!
                    val icon: Bitmap
                    if (currentConditions.observation.dayInd == "D") {
                        icon = WeatherIconManager.getInstance(context).getIcon(
                                Constants.WeatherComConstants.WEATHER_ICONS[currentConditions.observation.wxIcon]!!,
                                false)
                    } else {
                        /*
                         There are weird cases when there's no day/night indicator
                         */
                        icon = WeatherIconManager.getInstance(context).getIcon(
                                Constants.WeatherComConstants.WEATHER_ICONS[currentConditions.observation.wxIcon]!!,
                                true)
                    }
                    runOnMainThread {
                        updateData(LawnchairSmartspaceController.WeatherData(icon, Temperature(currentConditions.observation.temp, Temperature.Unit.Fahrenheit)), null)
                    }
                    d("updateData: retrieved current conditions ${currentConditions}")
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } else {
                if (!locationAccess) {
                    Utilities
                            .requestLocationPermission(context.lawnchairApp.activityHandler.foregroundActivity)
                    return@runOnUiWorkerThread
                }
                val locationProvider = locationManager?.getBestProvider(Criteria(), true)
                val location = locationManager?.getLastKnownLocation(locationProvider) ?: return@runOnUiWorkerThread
                val currentConditions =
                        WeatherComRetrofitServiceFactory.weatherComWeatherRetrofitService.getCurrentConditions(
                                location.latitude,
                                location.longitude).execute().body()!!
                val icon: Bitmap
                if (currentConditions.observation.dayInd == "D") {
                    icon = WeatherIconManager.getInstance(context).getIcon(
                            Constants.WeatherComConstants.WEATHER_ICONS[currentConditions.observation.wxIcon]!!,
                            false)
                } else {
                    /*
                     There are weird cases when there is no day/night indicator
                     */
                    icon = WeatherIconManager.getInstance(context).getIcon(
                            Constants.WeatherComConstants.WEATHER_ICONS[currentConditions.observation.wxIcon]!!,
                            false)
                }
                runOnMainThread {
                    updateData(LawnchairSmartspaceController.WeatherData(icon, Temperature(
                            currentConditions.observation.temp, Temperature.Unit.Fahrenheit)), null)
                }
                d("updateData: retrieved current conditions ${currentConditions}")
            }
        }
    }
}
