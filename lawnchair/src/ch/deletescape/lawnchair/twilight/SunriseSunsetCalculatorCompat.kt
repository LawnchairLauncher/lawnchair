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

package ch.deletescape.lawnchair.twilight

import android.location.Location
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location as SSLocation
import java.util.*

class SunriseSunsetCalculatorCompat(latitude: Double?, longitude: Double?, timeZone: TimeZone) {

    private val calculator = if (latitude != null && longitude != null)
        SunriseSunsetCalculator(SSLocation(latitude, longitude), timeZone)
    else null

    fun getOfficialSunriseCalendarForDate(calendar: Calendar): Calendar {
        return calculator?.getOfficialSunriseCalendarForDate(calendar) ?: cloneWithHour(calendar, 6)
    }

    fun getOfficialSunsetCalendarForDate(calendar: Calendar): Calendar {
        return calculator?.getOfficialSunsetCalendarForDate(calendar) ?: cloneWithHour(calendar, 20)
    }

    private fun cloneWithHour(src: Calendar, hour: Int): Calendar {
        return Calendar.getInstance(src.timeZone).apply {
            set(src.get(Calendar.YEAR), src.get(Calendar.MONTH), src.get(Calendar.DATE), hour, 0)
        }
    }
}
