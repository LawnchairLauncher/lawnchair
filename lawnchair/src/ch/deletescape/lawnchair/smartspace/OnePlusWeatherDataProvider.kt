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
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.annotation.Keep
import android.support.v4.content.ContextCompat
import android.util.Log
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Utilities
import com.android.launcher3.util.PackageManagerHelper
import net.oneplus.launcher.OPWeatherProvider
import java.lang.RuntimeException

@Keep
class OnePlusWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller), OPWeatherProvider.IWeatherCallback {

    private val context = controller.context
    private val provider by lazy { OPWeatherProvider(context) }

    init {
        if (!OnePlusWeatherDataProvider.isAvailable(context)) {
            throw RuntimeException("OP provider is not available")
        }
    }

    override fun performSetup() {
        provider.registerContentObserver(context.contentResolver)
        provider.subscribeCallback(this)
        super.performSetup()
    }

    override fun onWeatherUpdated(weatherData: OPWeatherProvider.WeatherData) {
        updateData(LawnchairSmartspaceController.WeatherData(
                getConditionIcon(weatherData),
                Temperature(weatherData.temperature, getTemperatureUnit(weatherData)),
                forecastIntent = Intent().setClassName(OPWeatherProvider.WEATHER_PACKAGE_NAME, OPWeatherProvider.WEATHER_LAUNCH_ACTIVITY)
        ), null)
    }

    private fun getConditionIcon(data: OPWeatherProvider.WeatherData):Bitmap {
        return BitmapFactory.decodeResource(context.resources, OPWeatherProvider.getWeatherIconResourceId(data.weatherCode))
    }

    private fun getTemperatureUnit(data: OPWeatherProvider.WeatherData): Temperature.Unit {
        return if (data.temperatureUnit == OPWeatherProvider.TEMP_UNIT_FAHRENHEIT) {
            Temperature.Unit.Fahrenheit
        } else Temperature.Unit.Celsius
    }


    override fun onDestroy() {
        super.onDestroy()
        provider.unregisterContentObserver(context.contentResolver)
        provider.unsubscribeCallback(this)
    }

    companion object {

        fun isAvailable(context: Context): Boolean {
            return PackageManagerHelper.isAppEnabled(context.packageManager, OPWeatherProvider.WEATHER_PACKAGE_NAME, 0)
        }
    }
}
