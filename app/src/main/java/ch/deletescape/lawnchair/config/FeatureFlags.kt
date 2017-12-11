/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.config

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.preferences.PreferenceProvider

/**
 * Defines a set of flags used to control various launcher behaviors
 */
object FeatureFlags {

    const val KEY_PREF_LIGHT_STATUS_BAR = "pref_forceLightStatusBar"
    const val KEY_PREF_PINCH_TO_OVERVIEW = "pref_pinchToOverview"
    const val KEY_PREF_PULLDOWN_NOTIS = "pref_pulldownNotis"
    const val KEY_PREF_HOTSEAT_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors"
    const val KEY_PREF_HAPTIC_FEEDBACK = "pref_enableHapticFeedback"
    const val KEY_PREF_KEEP_SCROLL_STATE = "pref_keepScrollState"
    const val KEY_FULL_WIDTH_SEARCHBAR = "pref_fullWidthSearchbar"
    const val KEY_SHOW_PIXEL_BAR = "pref_showPixelBar"
    const val KEY_HOME_OPENS_DRAWER = "pref_homeOpensDrawer"
    const val KEY_SHOW_VOICE_SEARCH_BUTTON = "pref_showMic"
    const val KEY_PREF_PIXEL_STYLE_ICONS = "pref_pixelStyleIcons"
    const val KEY_PREF_HIDE_APP_LABELS = "pref_hideAppLabels"
    const val KEY_PREF_ENABLE_SCREEN_ROTATION = "pref_enableScreenRotation"
    const val KEY_PREF_FULL_WIDTH_WIDGETS = "pref_fullWidthWidgets"
    const val KEY_PREF_SHOW_NOW_TAB = "pref_showGoogleNowTab"
    const val KEY_PREF_TRANSPARENT_HOTSEAT = "pref_isHotseatTransparent"
    const val KEY_PREF_ENABLE_DYNAMIC_UI = "pref_enableDynamicUi"
    const val KEY_PREF_ENABLE_BLUR = "pref_enableBlur"
    const val KEY_PREF_WHITE_GOOGLE_ICON = "pref_enableWhiteGoogleIcon"
    const val KEY_PREF_DARK_THEME = "pref_enableDarkTheme"
    const val KEY_PREF_ROUND_SEARCH_BAR = "pref_useRoundSearchBar"
    const val KEY_PREF_ENABLE_BACKPORT_SHORTCUTS = "pref_enableBackportShortcuts"
    const val KEY_PREF_SHOW_TOP_SHADOW = "pref_showTopShadow"
    const val KEY_PREF_THEME = "pref_theme"
    const val KEY_PREF_THEME_MODE = "pref_themeMode"
    const val KEY_PREF_HIDE_HOTSEAT = "pref_hideHotseat"
    const val KEY_PREF_PLANE = "pref_plane"
    const val KEY_PREF_WEATHER = "pref_weather"
    const val KEY_PREF_PULLDOWN_ACTION = "pref_pulldownAction"
    const val KEY_PREF_LOCK_DESKTOP = "pref_lockDesktop"
    const val KEY_PREF_ANIMATED_CLOCK_ICON = "pref_animatedClockIcon"
    const val KEY_PREF_SNOWFALL = "pref_snowfall"
    private var darkThemeFlag: Int = 0

    const val DARK_QSB = 1
    const val DARK_FOLDER = 2
    const val DARK_ALLAPPS = 4
    const val DARK_SHORTCUTS = 8
    const val DARK_BLUR = 16

    var currentTheme: Int = 0
    var useDarkTheme = true

    const val PULLDOWN_NOTIFICATIONS = 1
    const val PULLDOWN_SEARCH = 2
    const val PULLDOWN_APPS_SEARCH = 3

    fun pullDownAction(context: Context): Int {
        Utilities.getPrefs(context).migratePullDownPref(context)
        return Integer.parseInt(PreferenceProvider.getPreferences(context).pulldownAction)
    }

    fun loadThemePreference(context: Context) {
        val prefs = PreferenceProvider.getPreferences(context)
        currentTheme = Integer.parseInt(prefs.theme)
        useDarkTheme = currentTheme != 0
        darkThemeFlag = prefs.themeMode
    }

    fun useDarkTheme(flag: Int): Boolean {
        return useDarkTheme && darkThemeFlag and flag != 0
    }

    fun applyDarkTheme(context: Context, flag: Int): Context {
        if (useDarkTheme(flag)) {
            return ContextThemeWrapper(context, LAUNCHER_THEMES[currentTheme])
        } else {
            return context
        }
    }

    fun applyDarkTheme(activity: Activity) {
        Utilities.getPrefs(activity).migrateThemePref(activity)
        loadThemePreference(activity)
        if (FeatureFlags.useDarkTheme)
            activity.setTheme(SETTINGS_HOME_THEMES[currentTheme])
    }

    fun getLayoutInflator(layoutInflater: LayoutInflater) : LayoutInflater {
        val context = layoutInflater.context
        Utilities.getPrefs(context).migrateThemePref(context)
        loadThemePreference(context)
        return LayoutInflater.from(ContextThemeWrapper(context, SETTINGS_THEMES[currentTheme]))
    }

    private val LAUNCHER_THEMES = intArrayOf(R.style.LauncherTheme, R.style.LauncherTheme_Dark, R.style.LauncherTheme_Black)
    private val SETTINGS_THEMES = intArrayOf(R.style.SettingsTheme, R.style.SettingsTheme_Dark, R.style.SettingsTheme_Black)
    private val SETTINGS_HOME_THEMES = intArrayOf(R.style.SettingsHome, R.style.SettingsHome_Dark, R.style.SettingsHome_Black)
}
