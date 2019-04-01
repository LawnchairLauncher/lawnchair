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

package ch.deletescape.lawnchair.theme

import android.content.Context
import android.content.res.Configuration
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class ThemeManager(val context: Context) : WallpaperColorInfo.OnChangeListener {

    private val app = context.lawnchairApp
    private val wallpaperColorInfo = WallpaperColorInfo.getInstance(context)!!
    private val listeners = HashSet<ThemeOverride>()
    private val prefs = context.lawnchairPrefs
    private var themeFlags = 0
    private var usingNightMode = context.resources.configuration.usingNightMode
        set(value) {
            if (value != field) {
                field = value
                onExtractedColorsChanged(wallpaperColorInfo)
            }
        }

    val isDark get() = themeFlags and THEME_DARK != 0
    val supportsDarkText get() = themeFlags and THEME_DARK_TEXT != 0
    val displayName: String get() {
        val values = context.resources.getIntArray(R.array.themeValues)
        val strings = context.resources.getStringArray(R.array.themes)
        val index = values.indexOf(themeFlags)
        return strings.getOrNull(index) ?: context.resources.getString(R.string.theme_auto)
    }

    init {
        onExtractedColorsChanged(null)
        wallpaperColorInfo.addOnChangeListener(this)
    }

    fun addOverride(themeOverride: ThemeOverride) {
        synchronized(listeners) {
            removeDeadListeners()
            listeners.add(themeOverride)
        }
        themeOverride.applyTheme(themeFlags)
    }

    fun getCurrentFlags() = themeFlags

    private fun removeDeadListeners() {
        val it = listeners.iterator()
        while (it.hasNext()) {
            if (!it.next().isAlive) {
                it.remove()
            }
        }
    }

    override fun onExtractedColorsChanged(ignore: WallpaperColorInfo?) {
        val theme = prefs.launcherTheme
        val supportsDarkText: Boolean
        val isDark: Boolean
        val isBlack = isBlack(theme)
        if ((theme and THEME_AUTO) == 0) {
            supportsDarkText = isDarkText(theme)
            isDark = isDark(theme)
        } else {
            supportsDarkText = wallpaperColorInfo.supportsDarkText()
            isDark = if ((theme and THEME_AUTO_NIGHT_MODE == 0))
                wallpaperColorInfo.isDark
            else
                usingNightMode == true
        }
        var newFlags = 0
        if (supportsDarkText) newFlags = newFlags or THEME_DARK_TEXT
        if (isDark) newFlags = newFlags or THEME_DARK
        if (isBlack) newFlags = newFlags or THEME_USE_BLACK
        if (newFlags == themeFlags) return
        themeFlags = newFlags
        reloadActivities()
        synchronized(listeners) {
            removeDeadListeners()
            listeners.forEach { it.onThemeChanged(themeFlags) }
        }
    }

    private fun reloadActivities() {
        HashSet(app.activityHandler.activities).forEach {
            if (it is ThemeableActivity) {
                it.onThemeChanged()
            } else {
                it.recreate()
            }
        }
    }

    fun updateNightMode(newConfig: Configuration) {
        usingNightMode = newConfig.usingNightMode
    }

    interface ThemeableActivity {

        fun onThemeChanged()
    }

    companion object : SingletonHolder<ThemeManager, Context>(ensureOnMainThread(useApplicationContext(::ThemeManager))) {

        private const val THEME_AUTO = 1                     // 00001
        private const val THEME_DARK_TEXT = 1 shl 1          // 00010
        private const val THEME_DARK = 1 shl 2               // 00100
        private const val THEME_USE_BLACK = 1 shl 3          // 01000
        private const val THEME_AUTO_NIGHT_MODE = 1 shl 4    // 10000

        fun isDarkText(flags: Int) = (flags and THEME_DARK_TEXT) != 0
        fun isDark(flags: Int) = (flags and THEME_DARK) != 0
        fun isBlack(flags: Int) = (flags and THEME_USE_BLACK) != 0
    }
}
