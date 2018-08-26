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

import android.app.Activity
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.lang.ref.WeakReference

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

class ThemeOverride(private val themeSet: ThemeSet, val listener: ThemeOverrideListener) {

    constructor(themeSet: ThemeSet, activity: Activity) : this(themeSet, ActivityListener(activity))

    val isAlive get() = listener.isAlive

    fun applyTheme(themeFlags: Int) {
        if (ThemeManager.isDark(themeFlags)) {
            if (ThemeManager.isBlack(themeFlags)) {
                listener.applyTheme(themeSet.blackTheme)
            } else {
                listener.applyTheme(themeSet.darkTheme)
            }
        } else if (ThemeManager.isDarkText(themeFlags) && Utilities.ATLEAST_NOUGAT) {
            listener.applyTheme(themeSet.darkTextTheme)
        } else {
            listener.applyTheme(themeSet.lightTheme)
        }
    }

    fun onThemeChanged(themeFlags: Int) {
        applyTheme(themeFlags)
        listener.reloadTheme()
    }

    class Launcher : ThemeSet {

        override val lightTheme = R.style.LauncherTheme
        override val darkTextTheme = R.style.LauncherTheme_DarkText
        override val darkTheme = R.style.LauncherThemeDark
        override val blackTheme = R.style.LauncherThemeBlack
    }

    class LauncherQsb : ThemeSet {

        override val lightTheme = R.style.GoogleSearchLauncherTheme
        override val darkTextTheme = R.style.GoogleSearchLauncherThemeDarkText
        override val darkTheme = R.style.GoogleSearchLauncherThemeDark
        override val blackTheme = R.style.GoogleSearchLauncherThemeBlack
    }

    class LauncherScreenshot : ThemeSet {

        override val lightTheme = R.style.ScreenshotLauncherTheme
        override val darkTextTheme = R.style.ScreenshotLauncherThemeDarkText
        override val darkTheme = R.style.ScreenshotLauncherThemeDark
        override val blackTheme = R.style.ScreenshotLauncherThemeBlack
    }

    class Settings : ThemeSet {

        override val lightTheme = R.style.SettingsTheme_V2
        override val darkTextTheme = R.style.SettingsTheme_V2
        override val darkTheme = R.style.SettingsTheme_V2_Dark
        override val blackTheme = R.style.SettingsTheme_V2_Black
    }

    class SettingsTransparent : ThemeSet {

        override val lightTheme = R.style.SettingsTheme_V2_Transparent
        override val darkTextTheme = R.style.SettingsTheme_V2_DarkText_Transparent
        override val darkTheme = R.style.SettingsTheme_V2_Dark_Transparent
        override val blackTheme = R.style.SettingsTheme_V2_Black_Transparent
    }

    interface ThemeSet {

        val lightTheme: Int
        val darkTextTheme: Int
        val darkTheme: Int
        val blackTheme: Int
    }

    interface ThemeOverrideListener {

        val isAlive: Boolean

        fun applyTheme(themeRes: Int)
        fun reloadTheme()
    }

    class ActivityListener(activity: Activity) : ThemeOverrideListener {

        private val activityRef = WeakReference(activity)
        override val isAlive = activityRef.get() != null

        override fun applyTheme(themeRes: Int) {
            activityRef.get()?.setTheme(themeRes)
        }

        override fun reloadTheme() {
            activityRef.get()?.recreate()
        }
    }
}
