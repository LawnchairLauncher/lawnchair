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

import android.graphics.Bitmap
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.PeriodicDataProvider
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider
import ch.deletescape.lawnchair.smartspace.weathercom.Constants
import ch.deletescape.lawnchair.smartspace.weathercom.WeatherComRetrofitServiceFactory
import ch.deletescape.lawnchair.util.Temperature
import ch.deletescape.lawnchair.util.extensions.d

class WeatherChannelWeatherProvider(controller: LawnchairSmartspaceController) :
        PeriodicDataProvider(controller) {

    override fun updateData() {
        runOnNewThread {
            if (context.lawnchairPrefs.weatherCity != "##Auto") {
                d("updateData: updating weather (this call will fail)")
                try {
                    val currentConditions =
                            WeatherComRetrofitServiceFactory.weatherComWeatherRetrofitService.getCurrentConditions(
                                1.19482, 1.19482).execute().body()!!
                    val icon: Bitmap
                    if (currentConditions.observation.dayInd == "D") {
                        icon = WeatherIconProvider(context).getIcon(
                            Constants.WeatherComConstants.WEATHER_ICONS_DAY[currentConditions.observation.wxIcon].second)
                    } else {
                        /*
                         There are weird cases when there's no day/night indicator
                         */
                        icon = WeatherIconProvider(context).getIcon(
                            Constants.WeatherComConstants.WEATHER_ICONS_NIGHT[currentConditions.observation.wxIcon].second)
                    }
                    runOnMainThread {
                        updateData(LawnchairSmartspaceController.WeatherData(icon, Temperature(
                            currentConditions.observation.temp, Temperature.Unit.Fahrenheit), null,
                                                                             null, null,
                                                                             currentConditions.metadata.latitude,
                                                                             currentConditions.metadata.longitude,
                                                                             if (currentConditions.observation.dayInd == "D") {
                                                                                 Constants.WeatherComConstants.WEATHER_ICONS_DAY[currentConditions.observation.wxIcon]
                                                                                         .second
                                                                             } else {
                                                                                 /*
                                                                                  There are weird cases when there's no day/night indicator, for instance when the location is near the poles
                                                                                  */
                                                                                 Constants.WeatherComConstants.WEATHER_ICONS_NIGHT[currentConditions.observation.wxIcon]
                                                                                         .second
                                                                             }), null)
                    }
                    d("updateData: retrieved current conditions ${currentConditions}")
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                }
            } else {
                // TODO automatic location
            }
        }
    }
}
