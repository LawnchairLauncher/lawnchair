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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Criteria
import android.location.LocationManager
import android.location.LocationProvider
import android.provider.CalendarContract
import android.support.annotation.Keep
import android.support.v4.content.ContextCompat
import android.util.Log
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Utilities
import com.android.launcher3.util.PackageManagerHelper
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import net.oneplus.launcher.OPWeatherProvider
import java.lang.RuntimeException
import java.time.Instant
import java.util.*
import java.util.Calendar.HOUR_OF_DAY
import java.util.concurrent.TimeUnit

@Keep
class OnePlusWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller), OPWeatherProvider.IWeatherCallback {

    private val context = controller.context
    private val provider by lazy { OPWeatherProvider(context) }
    private val locationAccess by lazy { Utilities.hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            Utilities.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    private val locationManager: LocationManager? by lazy { if (locationAccess) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    } else null }

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
        // let's never again forget that unix timestamps is seconds, not millis
        val c = Calendar.getInstance().apply { timeInMillis = TimeUnit.SECONDS.toMillis(data.timestamp) }
        var isDay = c.get(HOUR_OF_DAY) in 6 until 20
        if (locationAccess) {
            val locationProvider = locationManager?.getBestProvider(Criteria(), true)
            val location = locationManager?.getLastKnownLocation(locationProvider)
            if (location != null) {
                val calc = SunriseSunsetCalculator(Location(location.latitude, location.longitude), c.timeZone)
                val sunrise = calc.getOfficialSunriseCalendarForDate(c)
                val sunset = calc.getOfficialSunsetCalendarForDate(c)
                isDay = sunrise.before(c) && sunset.after(c)
            }
        }

        val resId = if (isDay) {
            OPWeatherProvider.getWeatherIconResourceId(data.weatherCode)
        } else {
            OPWeatherProvider.getNightWeatherIconResourceId(data.weatherCode)
        }
        return BitmapFactory.decodeResource(context.resources, resId)
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
