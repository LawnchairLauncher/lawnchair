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
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.R
import java.lang.RuntimeException

class WeatherIconManager(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = context.lawnchairPrefs
    private val defaultPack =
            object : WeatherIconPack(context, context.getString(R.string.weather_icons_default), "",
                                     RecoloringMode.NEVER) {
                override val provider = DefaultIconProvider(context)
                override val icon = context.getDrawable(R.drawable.weather_04)
            }

    fun getIconPacks(): List<WeatherIconPack> = mutableListOf<WeatherIconPack>(defaultPack).apply {
        pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(
                        INTENT_CATEGORY), PackageManager.GET_META_DATA)?.map {
            val recoloringMode =
                    it.activityInfo.metaData?.getString(METADATA_KEY)?.let {
                        RecoloringMode.fromName(it)
                    } ?: RecoloringMode.NEVER
            WeatherIconPack(
                    context,
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    recoloringMode)
        }?.let { addAll(it) }
    }

    fun getIcon(which: Icon, night: Boolean) = getProvider().getIcon(which, night)

    fun getPack(): WeatherIconPack = getIconPacks().firstOrNull { it.pkgName == prefs.weatherIconPack }
                                     ?: defaultPack

    fun getProvider(): IconProvider = if (prefs.weatherIconPack == "")
        DefaultIconProvider(context)
    else
        getIconPacks().firstOrNull { it.pkgName == prefs.weatherIconPack }?.provider
        ?: DefaultIconProvider(context)


    companion object : LawnchairSingletonHolder<WeatherIconManager>(::WeatherIconManager) {
        const val INTENT_CATEGORY = "com.dvtonder.chronus.ICON_PACK"
        const val METADATA_KEY = "recoloringMode"
    }

    enum class Icon {
        NA,
        TORNADO,
        DUST,
        HURRICANE,
        SANDSTORM,
        SNOWSTORM,
        HAIL,
        WINDY,
        CLEAR,
        MOSTLY_CLEAR,
        PARTLY_CLOUDY,
        INTERMITTENT_CLOUDS,
        HAZY,
        MOSTLY_CLOUDY,
        SHOWERS,
        PARTLY_CLOUDY_W_SHOWERS,
        MOSTLY_CLOUDY_W_SHOWERS,
        PARTLY_CLOUDY_W_THUNDERSTORMS,
        MOSTLY_CLOUDY_W_THUNDERSTORMS,
        THUNDERSTORMS,
        FLURRIES,
        SNOW,
        ICE,
        RAIN_AND_SNOW,
        FREEZING_RAIN,
        SLEET,
        MOSTLY_CLOUDY_W_SNOW,
        PARTLY_CLOUDY_W_FLURRIES,
        MOSTLY_CLOUDY_W_FLURRIES,
        RAIN,
        FOG,
        OVERCAST,
        CLOUDY
    }

    enum class RecoloringMode {
        ALWAYS("always"),
        IF_NEEDED("ifNeeded"),
        NEVER("never");

        val metaName: String

        constructor(name: String) {
            metaName = name
        }

        companion object {
            fun fromName(name: String) = when (name) {
                ALWAYS.metaName -> ALWAYS
                IF_NEEDED.metaName -> IF_NEEDED
                NEVER.metaName -> NEVER
                else -> throw RuntimeException()
            }
        }

    }

    open class WeatherIconPack(val context: Context, val name: String, val pkgName: String,
                               val recoloringMode: RecoloringMode) {
        open val provider: IconProvider by lazy { WeatherIconPackProviderImpl(context, pkgName, this) }
        open val icon by lazy { context.packageManager.getApplicationIcon(pkgName) }
    }

    interface IconProvider {
        fun getIcon(which: Icon, night: Boolean = false): Bitmap
    }
}