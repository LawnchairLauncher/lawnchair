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
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.support.annotation.Keep
import android.support.v4.content.ContextCompat
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Utilities
import java.lang.RuntimeException

@Keep
class PEWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller) {

    private val contentResolver = context.contentResolver

    init {
        if (!PEWeatherDataProvider.isAvailable(context)) {
            throw RuntimeException("PE provider is not available")
        }
    }

    override fun queryWeatherData() : LawnchairSmartspaceController.WeatherData? {
        contentResolver.query(weatherUri, PROJECTION_DEFAULT_WEATHER, null, null, null)?.use { cursor ->
            val count = cursor.count
            if (count > 0) {
                cursor.moveToPosition(0)
                val status = cursor.getInt(0)
                if (status == 0) {
                    val conditions = cursor.getString(1)
                    val temperature = cursor.getInt(2)
                    return LawnchairSmartspaceController.WeatherData(getConditionIcon(conditions),
                            Temperature(temperature, Temperature.Unit.Celsius), "")
                }
            }
        }
        return null
    }

    private fun getConditionIcon(condition: String): Bitmap {
        val resName = when(condition) {
            "partly-cloudy" -> "weather_partly_cloudy"
            "partly-cloudy-night" -> "weather_partly_cloudy_night"
            "mostly-cloudy" -> "weather_mostly_cloudy"
            "mostly-cloudy-night" -> "weather_mostly_cloudy_night"
            "cloudy" -> "weather_cloudy"
            "clear-night" -> "weather_clear_night"
            "mostly-clear-night" -> "weather_mostly_clear_night"
            "sunny" -> "weather_sunny"
            "mostly-sunny" -> "weather_mostly_sunny"
            "scattered-showers" -> "weather_scattered_showers"
            "scattered-showers-night" -> "weather_scattered_showers_night"
            "rain" -> "weather_rain"
            "windy" -> "weather_windy"
            "snow" -> "weather_snow"
            "scattered-thunderstorms" -> "weather_isolated_scattered_thunderstorms"
            "scattered-thunderstorms-night" -> "weather_isolated_scattered_thunderstorms_night"
            "isolated-thunderstorms" -> "weather_isolated_scattered_thunderstorms"
            "isolated-thunderstorms-night" -> "weather_isolated_scattered_thunderstorms_night"
            "thunderstorms" -> "weather_thunderstorms"
            "foggy" -> "weather_foggy"
            else -> null
        }
        val res = context.resources
        val resId = res.getIdentifier(resName, "drawable", "android")
        return Utilities.drawableToBitmap(context.resources.getDrawable(resId))!!
    }

    companion object {

        private const val authority = "org.pixelexperience.weather.client.provider"
        private val weatherUri = Uri.parse("content://$authority/weather")!!

        private const val statusColumn = "status"
        private const val conditionsColumn = "conditions"
        private const val metricTemperatureColumn = "temperatureMetric"
        private const val imperialTemperatureColumn = "temperatureImperial"
        private val PROJECTION_DEFAULT_WEATHER = arrayOf(statusColumn, conditionsColumn, metricTemperatureColumn, imperialTemperatureColumn)

        fun isAvailable(context: Context): Boolean {
            val providerInfo = context.packageManager.resolveContentProvider(authority, 0) ?: return false
            return ContextCompat.checkSelfPermission(context, providerInfo.readPermission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
