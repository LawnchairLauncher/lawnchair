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
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.R

// See: https://github.com/DvTonder/Sample_icon_set
class WeatherIconPackProviderImpl(private val context: Context, private val pkgName: String, private val pack: WeatherIconManager.WeatherIconPack): WeatherIconManager.IconProvider {
    private val res = context.packageManager.getResourcesForApplication(pkgName)
    private val tintColor get() = ColorEngine.getInstance(context).getResolver(ColorEngine.Resolvers.WORKSPACE_ICON_LABEL).resolveColor()

    override fun getIcon(which: WeatherIconManager.Icon, night: Boolean): Bitmap {
        val resId = res.getIdentifier(getResName(which, night), "drawable", pkgName)
        return (if (resId > 0) res.getDrawable(resId) else context.getDrawable(R.drawable.weather_none_available)).apply {
            if (pack.recoloringMode == WeatherIconManager.RecoloringMode.ALWAYS) {
                setTint(tintColor)
            }
        }.toBitmap()!!
    }

    companion object {
        const val NA = "na"
        const val PREFIX = "weather_"
        val MAP = mapOf(
                WeatherIconManager.Icon.NA to entry(NA),
                WeatherIconManager.Icon.TORNADO to entry("0"),
                WeatherIconManager.Icon.HURRICANE to entry("2"),
                WeatherIconManager.Icon.WINDY to entry("24"),
                WeatherIconManager.Icon.DUST to entry("19"),
                WeatherIconManager.Icon.SNOWSTORM to entry("43"),
                WeatherIconManager.Icon.HAIL to entry("17"),
                WeatherIconManager.Icon.CLEAR to entry("32", "31"),
                WeatherIconManager.Icon.MOSTLY_CLEAR to entry("34", "33"),
                WeatherIconManager.Icon.PARTLY_CLOUDY to entry("30", "29"),
                WeatherIconManager.Icon.INTERMITTENT_CLOUDS to entry("28", "27"),
                WeatherIconManager.Icon.HAZY to entry("21"),
                WeatherIconManager.Icon.MOSTLY_CLOUDY to entry("28", "27"),
                WeatherIconManager.Icon.SHOWERS to entry("11"),
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_SHOWERS to entry("12"),
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SHOWERS to entry("12"),
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_THUNDERSTORMS to entry("37", "47"),
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_THUNDERSTORMS to entry("39", "38"),
                WeatherIconManager.Icon.THUNDERSTORMS to entry("4", "45"),
                WeatherIconManager.Icon.FLURRIES to entry("13"),
                WeatherIconManager.Icon.SNOW to entry("16"),
                WeatherIconManager.Icon.ICE to entry("25"),
                WeatherIconManager.Icon.RAIN_AND_SNOW to entry("5"),
                WeatherIconManager.Icon.FREEZING_RAIN to entry("10"),
                WeatherIconManager.Icon.SLEET to entry("18"),
                // TODO: improve these few mappings
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_SNOW to entry("16"),
                WeatherIconManager.Icon.PARTLY_CLOUDY_W_FLURRIES to entry("16"),
                WeatherIconManager.Icon.MOSTLY_CLOUDY_W_FLURRIES to entry("16"),
                WeatherIconManager.Icon.RAIN to entry("12"),
                WeatherIconManager.Icon.FOG to entry("20"),
                WeatherIconManager.Icon.OVERCAST to entry("26"),
                WeatherIconManager.Icon.CLOUDY to entry("26"))

        private inline fun entry(day: String, night: String? = null) = IconMapEntry(day, night)

        fun getResName(icon: WeatherIconManager.Icon, night: Boolean) = PREFIX + (MAP[icon]?.getEntry(night) ?: NA)
    }

    data class IconMapEntry(private val day: String, private val night: String? = null) {
        fun getEntry(night: Boolean) = if (night && this.night != null) this.night else day
    }
}