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

import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnUiWorkerThread
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.PeriodicDataProvider
import ch.deletescape.lawnchair.util.extensions.d

class WeatherChannelWeatherProvider(controller: LawnchairSmartspaceController) :
        PeriodicDataProvider(controller) {

    override fun updateData() {
        runOnUiWorkerThread {
            if (context.lawnchairPrefs.weatherCity != "##Auto") {
                d("updateData: updating weather (this call will fail)")
                try {
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // TODO automatic location
            }
        }
    }
}
