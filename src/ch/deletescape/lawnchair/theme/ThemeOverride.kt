package ch.deletescape.lawnchair.theme

import android.app.Activity
import com.android.launcher3.R
import com.android.launcher3.Utilities

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

abstract class ThemeOverride(val activity: Activity) {

    abstract val lightTheme: Int
    abstract val darkTextTheme: Int
    abstract val darkTheme: Int
    abstract val blackTheme: Int

    fun overrideTheme(themeFlags: Int) {
        if (ThemeManager.isDark(themeFlags)) {
            if (ThemeManager.isBlack(themeFlags)) {
                activity.setTheme(blackTheme)
            } else {
                activity.setTheme(darkTheme)
            }
        } else if (ThemeManager.isDarkText(themeFlags) && Utilities.ATLEAST_NOUGAT) {
            activity.setTheme(darkTextTheme)
        } else {
            activity.setTheme(lightTheme)
        }
    }

    fun onThemeChanged(themeFlags: Int) {
        overrideTheme(themeFlags)
        activity.recreate()
    }

    class Launcher(activity: Activity) : ThemeOverride(activity) {

        override val lightTheme = R.style.GoogleSearchLauncherTheme
        override val darkTextTheme = R.style.GoogleSearchLauncherThemeDarkText
        override val darkTheme = R.style.GoogleSearchLauncherThemeDark
        override val blackTheme = R.style.GoogleSearchLauncherThemeBlack
    }

    class Settings(activity: Activity) : ThemeOverride(activity) {

        override val lightTheme = R.style.SettingsTheme_V2
        override val darkTextTheme = R.style.SettingsTheme_V2
        override val darkTheme = R.style.SettingsTheme_V2_Dark
        override val blackTheme = R.style.SettingsTheme_V2_Black
    }
}