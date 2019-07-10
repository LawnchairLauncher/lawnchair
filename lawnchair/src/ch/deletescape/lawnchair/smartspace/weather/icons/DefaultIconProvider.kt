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

package ch.deletescape.lawnchair.smartspace.weather.icons

import android.content.Context
import android.graphics.Bitmap
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.R

class DefaultIconProvider(private val context: Context): WeatherIconManager.IconProvider {
    override fun getIcon(which: WeatherIconManager.Icon, night: Boolean): Bitmap {
        return context.resources.getDrawable((if (night) NIGHT_MAP[which] else DAY_MAP[which]) ?: NA).toBitmap()!!
    }

    companion object {
        private const val NA = R.drawable.weather_none_available
        private val DAY_MAP = mapOf<WeatherIconManager.Icon, Int>(
                WeatherIconManager.Icon.NA to R.drawable.weather_none_available,
                WeatherIconManager.Icon.DUST to R.drawable.weather_50,
                WeatherIconManager.Icon.SNOWSTORM to R.drawable.weather_13,
                WeatherIconManager.Icon.HAIL to R.drawable.weather_10,
                WeatherIconManager.Icon.CLEAR to R.drawable.weather_01,
                WeatherIconManager.Icon.MOSTLY_CLEAR to R.drawable.weather_02,
                WeatherIconManager.Icon.PARTLY_CLOUDY to R.drawable.weather_02,
                WeatherIconManager.Icon.INTERMITTENT_CLOUDS to R.drawable.weather_03,
                WeatherIconManager.Icon.HAZY to R.drawable.weather_50,
                WeatherIconManager.Icon.MOSTLY_CLOUDY to R.drawable.weather_04,
                WeatherIconManager.Icon.SHOWERS to R.drawable.weather_10,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_SHOWERS to R.drawable.weather_10,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SHOWERS to R.drawable.weather_09,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.ICE to R.drawable.weather_13,
                WeatherIconManager.Icon.RAIN_AND_SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.FREEZING_RAIN to R.drawable.weather_10,
                WeatherIconManager.Icon.SLEET to R.drawable.weather_13,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.RAIN to R.drawable.weather_09,
                WeatherIconManager.Icon.FOG to R.drawable.weather_50,
                WeatherIconManager.Icon.OVERCAST to R.drawable.weather_04,
                WeatherIconManager.Icon.CLOUDY to R.drawable.weather_04)
        private val NIGHT_MAP = mapOf<WeatherIconManager.Icon, Int>(
                WeatherIconManager.Icon.NA to R.drawable.weather_none_available,
                WeatherIconManager.Icon.DUST to R.drawable.weather_50,
                WeatherIconManager.Icon.SNOWSTORM to R.drawable.weather_13,
                WeatherIconManager.Icon.HAIL to R.drawable.weather_10n,
                WeatherIconManager.Icon.CLEAR to R.drawable.weather_01n,
                WeatherIconManager.Icon.MOSTLY_CLEAR to R.drawable.weather_02n,
                WeatherIconManager.Icon.PARTLY_CLOUDY to R.drawable.weather_02n,
                WeatherIconManager.Icon.INTERMITTENT_CLOUDS to R.drawable.weather_03n,
                WeatherIconManager.Icon.HAZY to R.drawable.weather_50,
                WeatherIconManager.Icon.MOSTLY_CLOUDY to R.drawable.weather_04n,
                WeatherIconManager.Icon.SHOWERS to R.drawable.weather_10n,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_SHOWERS to R.drawable.weather_10n,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SHOWERS to R.drawable.weather_09,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.THUNDERSTORMS to R.drawable.weather_11,
                WeatherIconManager.Icon.FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.ICE to R.drawable.weather_13,
                WeatherIconManager.Icon.RAIN_AND_SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.FREEZING_RAIN to R.drawable.weather_10n,
                WeatherIconManager.Icon.SLEET to R.drawable.weather_13,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SNOW to R.drawable.weather_13,
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_FLURRIES to R.drawable.weather_13,
                WeatherIconManager.Icon.RAIN to R.drawable.weather_09,
                WeatherIconManager.Icon.FOG to R.drawable.weather_50,
                WeatherIconManager.Icon.OVERCAST to R.drawable.weather_04n,
                WeatherIconManager.Icon.CLOUDY to R.drawable.weather_04n)
    }
}