/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui.controllers

import android.content.Context
import android.support.annotation.Keep
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.PreferenceController
import ch.deletescape.lawnchair.smartspace.AccuWeatherDataProvider
import ch.deletescape.lawnchair.smartspace.OnePlusWeatherDataProvider
import ch.deletescape.lawnchair.smartspace.weather.weathercom.WeatherChannelWeatherProvider

@Keep
class WeatherIconPackController(context: Context) : PreferenceController(context) {

    override val isVisible = context.lawnchairPrefs.weatherProvider in SUPPORTED

    companion object {
        private val SUPPORTED = listOf(
                OnePlusWeatherDataProvider::class.java.name,
                AccuWeatherDataProvider::class.java.name,
                WeatherChannelWeatherProvider::class.java.name)
    }
}