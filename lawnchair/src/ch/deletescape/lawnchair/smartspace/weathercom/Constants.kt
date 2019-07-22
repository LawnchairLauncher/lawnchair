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

package ch.deletescape.lawnchair.smartspace.weathercom

import ch.deletescape.lawnchair.smartspace.WeatherIconProvider


object Constants {

    object WeatherComConstants {
        val WEATHER_COM_API_KEY = "8de2d8b3a93542c9a2d8b3a935a2c909"
        private val WEATHER_ICONS = mapOf(0 to WeatherIconProvider.CONDITION_STORM,
                                          1 to WeatherIconProvider.CONDITION_STORM,
                                          2 to WeatherIconProvider.CONDITION_STORM,
                                          3 to WeatherIconProvider.CONDITION_STORM,
                                          4 to WeatherIconProvider.CONDITION_STORM,
                                          5 to WeatherIconProvider.CONDITION_SNOW,
                                          6 to WeatherIconProvider.CONDITION_SNOW,
                                          7 to WeatherIconProvider.CONDITION_SNOW,
                                          8 to WeatherIconProvider.CONDITION_SHOWERS,
                                          9 to WeatherIconProvider.CONDITION_SHOWERS,
                                          10 to WeatherIconProvider.CONDITION_RAIN,
                                          11 to WeatherIconProvider.CONDITION_RAIN,
                                          12 to WeatherIconProvider.CONDITION_RAIN,
                                          13 to WeatherIconProvider.CONDITION_SNOW,
                                          14 to WeatherIconProvider.CONDITION_SNOW,
                                          15 to WeatherIconProvider.CONDITION_SNOW,
                                          16 to WeatherIconProvider.CONDITION_SNOW,
                                          17 to WeatherIconProvider.CONDITION_SNOW,
                                          18 to WeatherIconProvider.CONDITION_SNOW,
                                          19 to WeatherIconProvider.CONDITION_MIST,
                                          21 to WeatherIconProvider.CONDITION_MIST,
                                          22 to WeatherIconProvider.CONDITION_MIST,
                                          23 to WeatherIconProvider.CONDITION_CLOUDS,
                                          24 to WeatherIconProvider.CONDITION_CLOUDS,
                                          25 to WeatherIconProvider.CONDITION_CLOUDS,
                                          23 to WeatherIconProvider.CONDITION_CLOUDS,
                                          24 to WeatherIconProvider.CONDITION_CLOUDS,
                                          25 to WeatherIconProvider.CONDITION_CLOUDS,
                                          26 to WeatherIconProvider.CONDITION_MOST_CLOUDS,
                                          27 to WeatherIconProvider.CONDITION_MOST_CLOUDS,
                                          28 to WeatherIconProvider.CONDITION_CLOUDS,
                                          29 to WeatherIconProvider.CONDITION_CLOUDS,
                                          30 to WeatherIconProvider.CONDITION_CLOUDS,
                                          31 to WeatherIconProvider.CONDITION_CLEAR,
                                          32 to WeatherIconProvider.CONDITION_CLEAR,
                                          33 to WeatherIconProvider.CONDITION_MOST_CLOUDS,
                                          34 to WeatherIconProvider.CONDITION_MOST_CLOUDS,
                                          35 to WeatherIconProvider.CONDITION_RAIN,
                                          36 to WeatherIconProvider.CONDITION_CLEAR,
                                          37 to WeatherIconProvider.CONDITION_STORM,
                                          38 to WeatherIconProvider.CONDITION_STORM,
                                          39 to WeatherIconProvider.CONDITION_SHOWERS,
                                          40 to WeatherIconProvider.CONDITION_RAIN,
                                          41 to WeatherIconProvider.CONDITION_RAIN,
                                          42 to WeatherIconProvider.CONDITION_SNOW,
                                          43 to WeatherIconProvider.CONDITION_SNOW,
                                          44 to WeatherIconProvider.CONDITION_UNKNOWN,
                                          45 to WeatherIconProvider.CONDITION_SHOWERS,
                                          46 to WeatherIconProvider.CONDITION_SNOW,
                                          47 to WeatherIconProvider.CONDITION_STORM)
        val WEATHER_ICONS_DAY = WEATHER_ICONS.map { it.key to it.value + "d" }
        val WEATHER_ICONS_NIGHT = WEATHER_ICONS.map { it.key to it.value + "n" }
    }
}
