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
import android.content.Context
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

class ThemeOverride(private val themeSet: ThemeSet, val listener: ThemeOverrideListener?) {

    constructor(themeSet: ThemeSet, activity: Activity) : this(themeSet, ActivityListener(activity))
    constructor(themeSet: ThemeSet, context: Context) : this(themeSet, ContextListener(context))

    val isAlive get() = listener?.isAlive == true

    fun applyTheme(context: Context) {
        listener?.applyTheme(getTheme(context))
    }

    fun applyTheme(themeFlags: Int) {
        listener?.applyTheme(getTheme(themeFlags))
    }

    fun getTheme(context: Context): Int {
        return themeSet.getTheme(context)
    }

    fun getTheme(themeFlags: Int) = themeSet.getTheme(themeFlags)

    fun onThemeChanged(themeFlags: Int) {
        listener?.reloadTheme()
    }

    class Launcher : ThemeSet {

        override val lightTheme = R.style.AppTheme
        override val darkTextTheme = R.style.AppTheme_DarkText
        override val darkTheme = R.style.AppTheme_Dark
        override val darkDarkTextTheme = R.style.AppTheme_Dark_DarkText
        override val blackTheme = R.style.AppTheme_Black
        override val blackDarkTextTheme = R.style.AppTheme_Black_DarkText
    }

    class LauncherScreenshot : ThemeSet {

        override val lightTheme = R.style.ScreenshotTheme
        override val darkTextTheme = R.style.ScreenshotTheme_DarkText
        override val darkTheme = R.style.ScreenshotTheme_Dark
        override val darkDarkTextTheme = R.style.ScreenshotTheme_Dark_DarkText
        override val blackTheme = R.style.ScreenshotTheme_Black
        override val blackDarkTextTheme = R.style.ScreenshotTheme_Black_DarkText
    }

    class Settings : ThemeSet {

        override val lightTheme = R.style.SettingsTheme_V2
        override val darkTextTheme = R.style.SettingsTheme_V2
        override val darkTheme = R.style.SettingsTheme_V2_Dark
        override val darkDarkTextTheme = R.style.SettingsTheme_V2_Dark
        override val blackTheme = R.style.SettingsTheme_V2_Black
        override val blackDarkTextTheme = R.style.SettingsTheme_V2_Black
    }

    class SettingsTransparent : ThemeSet {

        override val lightTheme = R.style.SettingsTheme_V2_Transparent
        override val darkTextTheme = R.style.SettingsTheme_V2_DarkText_Transparent
        override val darkTheme = R.style.SettingsTheme_V2_Dark_Transparent
        override val darkDarkTextTheme = R.style.SettingsTheme_V2_Dark_Transparent
        override val blackTheme = R.style.SettingsTheme_V2_Black_Transparent
        override val blackDarkTextTheme = R.style.SettingsTheme_V2_Black_Transparent
    }

    class LauncherDialog : ThemeSet {

        override val lightTheme = android.R.style.Theme_Material_Light
        override val darkTextTheme = android.R.style.Theme_Material_Light
        override val darkTheme = android.R.style.Theme_Material
        override val darkDarkTextTheme = android.R.style.Theme_Material
        override val blackTheme = android.R.style.Theme_Material
        override val blackDarkTextTheme = android.R.style.Theme_Material
    }

    class DeviceDefault : ThemeSet {

        override val lightTheme = android.R.style.Theme_DeviceDefault_Light
        override val darkTextTheme = android.R.style.Theme_DeviceDefault_Light
        override val darkTheme = android.R.style.Theme_DeviceDefault
        override val darkDarkTextTheme = android.R.style.Theme_DeviceDefault
        override val blackTheme = android.R.style.Theme_DeviceDefault
        override val blackDarkTextTheme = android.R.style.Theme_DeviceDefault
    }

    class AlertDialog : ThemeSet {

        override val lightTheme = R.style.SettingsTheme_V2_Dialog
        override val darkTextTheme = R.style.SettingsTheme_V2_Dialog
        override val darkTheme = R.style.SettingsTheme_V2_Dark_Dialog
        override val darkDarkTextTheme = R.style.SettingsTheme_V2_Dark_Dialog
        override val blackTheme = R.style.SettingsTheme_V2_Dark_Dialog
        override val blackDarkTextTheme = R.style.SettingsTheme_V2_Dark_Dialog
    }

    interface ThemeSet {

        val lightTheme: Int
        val darkTextTheme: Int
        val darkTheme: Int
        val darkDarkTextTheme: Int
        val blackTheme: Int
        val blackDarkTextTheme: Int

        fun getTheme(context: Context): Int {
            return getTheme(ThemeManager.getInstance(context).getCurrentFlags())
        }

        fun getTheme(themeFlags: Int): Int {
            val isDark = ThemeManager.isDark(themeFlags)
            val isDarkText = ThemeManager.isDarkText(themeFlags) && Utilities.ATLEAST_MARSHMALLOW
            val isBlack = isDark && ThemeManager.isBlack(themeFlags)
            return when {
                isBlack && isDarkText -> blackDarkTextTheme
                isBlack -> blackTheme
                isDark && isDarkText -> darkDarkTextTheme
                isDark -> darkTheme
                isDarkText -> darkTextTheme
                else -> lightTheme
            }
        }
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

    class ContextListener(context: Context) : ThemeOverrideListener {

        private val contextRef = WeakReference(context)
        override val isAlive = contextRef.get() != null

        override fun applyTheme(themeRes: Int) {
            contextRef.get()?.setTheme(themeRes)
        }

        override fun reloadTheme() {
            // Unsupported
        }
    }
}
