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
import android.util.Log
import com.android.launcher3.R
import java.util.*

class WeatherIconProvider(private val context: Context) {

    fun getIcon(iconID: String?): Bitmap {
        var resID = iconID
        if (!ID_MAP.containsKey(resID)) {
            Log.e("WeatherIconProvider", "No weather icon exists for condition: $resID")
            resID = CONDITION_UNKNOWN
        }

        return BitmapFactory.decodeResource(context.resources, ID_MAP[resID]!!)
    }

    companion object {
        const val CONDITION_UNKNOWN = "-1"
        const val CONDITION_CLEAR = "01"
        const val CONDITION_FEW_CLOUDS = "02"
        const val CONDITION_CLOUDS = "03"
        const val CONDITION_MOST_CLOUDS = "04"
        const val CONDITION_SHOWERS = "09"
        const val CONDITION_RAIN = "10"
        const val CONDITION_STORM = "11"
        const val CONDITION_SNOW = "13"
        const val CONDITION_MIST = "50"

        private val ID_MAP = mapOf(
                "${CONDITION_CLEAR}d" to R.drawable.weather_01,
                "${CONDITION_CLEAR}n" to R.drawable.weather_01n,
                "${CONDITION_FEW_CLOUDS}d" to R.drawable.weather_02,
                "${CONDITION_FEW_CLOUDS}n" to R.drawable.weather_02n,
                "${CONDITION_CLOUDS}d" to R.drawable.weather_03,
                "${CONDITION_CLOUDS}n" to R.drawable.weather_03n,
                "${CONDITION_MOST_CLOUDS}d" to R.drawable.weather_04,
                "${CONDITION_MOST_CLOUDS}n" to R.drawable.weather_04n,
                "${CONDITION_SHOWERS}d" to R.drawable.weather_09,
                "${CONDITION_SHOWERS}n" to R.drawable.weather_09,
                "${CONDITION_RAIN}d" to R.drawable.weather_10,
                "${CONDITION_RAIN}n" to R.drawable.weather_10n,
                "${CONDITION_STORM}d" to R.drawable.weather_11,
                "${CONDITION_STORM}n" to R.drawable.weather_11,
                "${CONDITION_SNOW}d" to R.drawable.weather_13,
                "${CONDITION_SNOW}n" to R.drawable.weather_13,
                "${CONDITION_MIST}d" to R.drawable.weather_50,
                "${CONDITION_MIST}n" to R.drawable.weather_50,
                CONDITION_UNKNOWN to R.drawable.weather_none_available
        )
    }
}