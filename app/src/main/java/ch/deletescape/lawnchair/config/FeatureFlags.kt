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
import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities

/**
 * Defines a set of flags used to control various launcher behaviors
 */
object FeatureFlags {

    private const val KEY_PREF_LIGHT_STATUS_BAR = "pref_forceLightStatusBar"
    private const val KEY_PREF_PINCH_TO_OVERVIEW = "pref_pinchToOverview"
    private const val KEY_PREF_PULLDOWN_NOTIS = "pref_pulldownNotis"
    private const val KEY_PREF_HOTSEAT_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors"
    private const val KEY_PREF_HAPTIC_FEEDBACK = "pref_enableHapticFeedback"
    private const val KEY_PREF_KEEP_SCROLL_STATE = "pref_keepScrollState"
    private const val KEY_FULL_WIDTH_SEARCHBAR = "pref_fullWidthSearchbar"
    private const val KEY_SHOW_PIXEL_BAR = "pref_showPixelBar"
    private const val KEY_HOME_OPENS_DRAWER = "pref_homeOpensDrawer"
    const val KEY_SHOW_VOICE_SEARCH_BUTTON = "pref_showMic"
    private const val KEY_PREF_PIXEL_STYLE_ICONS = "pref_pixelStyleIcons"
    private const val KEY_PREF_HIDE_APP_LABELS = "pref_hideAppLabels"
    private const val KEY_PREF_ENABLE_SCREEN_ROTATION = "pref_enableScreenRotation"
    private const val KEY_PREF_FULL_WIDTH_WIDGETS = "pref_fullWidthWidgets"
    private const val KEY_PREF_SHOW_NOW_TAB = "pref_showGoogleNowTab"
    private const val KEY_PREF_TRANSPARENT_HOTSEAT = "pref_isHotseatTransparent"
    private const val KEY_PREF_ENABLE_DYNAMIC_UI = "pref_enableDynamicUi"
    private const val KEY_PREF_ENABLE_BLUR = "pref_enableBlur"
    const val KEY_PREF_WHITE_GOOGLE_ICON = "pref_enableWhiteGoogleIcon"
    private const val KEY_PREF_DARK_THEME = "pref_enableDarkTheme"
    private const val KEY_PREF_ROUND_SEARCH_BAR = "pref_useRoundSearchBar"
    private const val KEY_PREF_ENABLE_BACKPORT_SHORTCUTS = "pref_enableBackportShortcuts"
    private const val KEY_PREF_SHOW_TOP_SHADOW = "pref_showTopShadow"
    const val KEY_PREF_THEME = "pref_theme"
    private const val KEY_PREF_THEME_MODE = "pref_themeMode"
    private const val KEY_PREF_HIDE_HOTSEAT = "pref_hideHotseat"
    private const val KEY_PREF_PLANE = "pref_plane"
    private const val KEY_PREF_WEATHER = "pref_weather"
    private const val KEY_PREF_PULLDOWN_ACTION = "pref_pulldownAction"
    private const val KEY_PREF_ENABLE_EDITING = "pref_enableEditing"
    private const val KEY_PREF_ANIMATED_CLOCK_ICON = "pref_animatedClockIcon"
    private var darkThemeFlag: Int = 0

    fun pinchToOverview(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PINCH_TO_OVERVIEW, true)
    }

    // When enabled the status bar may show dark icons based on the top of the wallpaper.
    fun lightStatusBar(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_LIGHT_STATUS_BAR, false)
    }

    fun hotseatShouldUseExtractedColors(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HOTSEAT_EXTRACTED_COLORS, true)
    }

    fun enableHapticFeedback(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HAPTIC_FEEDBACK, false)
    }

    fun keepScrollState(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_KEEP_SCROLL_STATE, false)
    }

    fun useFullWidthSearchbar(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_FULL_WIDTH_SEARCHBAR, false)
    }

    fun showVoiceSearchButton(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_SHOW_VOICE_SEARCH_BUTTON, false)
    }

    fun showPixelBar(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_SHOW_PIXEL_BAR, true)
    }

    fun homeOpensDrawer(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_HOME_OPENS_DRAWER, true)
    }

    fun usePixelIcons(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PIXEL_STYLE_ICONS, true)
    }

    fun enableScreenRotation(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_SCREEN_ROTATION, false)
    }

    fun hideAppLabels(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HIDE_APP_LABELS, false)
    }

    fun allowFullWidthWidgets(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_FULL_WIDTH_WIDGETS, false)
    }

    fun showGoogleNowTab(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_SHOW_NOW_TAB, true)
    }

    fun isTransparentHotseat(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_TRANSPARENT_HOTSEAT, false)
    }

    fun isDynamicUiEnabled(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_DYNAMIC_UI, false)
    }

    fun isBlurEnabled(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_BLUR, false)
    }

    fun useWhiteGoogleIcon(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_WHITE_GOOGLE_ICON, false)
    }

    const val DARK_QSB = 1
    const val DARK_FOLDER = 2
    const val DARK_ALLAPPS = 4
    const val DARK_SHORTCUTS = 8
    const val DARK_BLUR = 16

    var currentTheme: Int = 0
    var useDarkTheme = true

    private fun migrateThemePref(context: Context) {
        val darkTheme = Utilities.getPrefs(context).getBoolean(KEY_PREF_DARK_THEME, false)
        if (darkTheme) {
            Utilities.getPrefs(context).edit()
                    .remove(KEY_PREF_DARK_THEME)
                    .putString(KEY_PREF_THEME, "1")
                    .apply()
        }
    }

    private fun migratePullDownPref(context: Context) {
        val pulldownNotis = Utilities.getPrefs(context).getBoolean(KEY_PREF_PULLDOWN_NOTIS, true)
        if (!pulldownNotis) {
            Utilities.getPrefs(context).edit()
                    .remove(KEY_PREF_PULLDOWN_NOTIS)
                    .putString(KEY_PREF_PULLDOWN_ACTION, "0")
                    .apply()
        }
    }

    fun pullDownAction(context: Context): Int {
        migratePullDownPref(context)
        return Integer.parseInt(Utilities.getPrefs(context).getString(KEY_PREF_PULLDOWN_ACTION, "1"))
    }

    fun loadDarkThemePreference(context: Context) {
        val prefs = Utilities.getPrefs(context)
        currentTheme = Integer.parseInt(prefs.getString(KEY_PREF_THEME, "0"))
        useDarkTheme = currentTheme != 0
        darkThemeFlag = prefs.getInt(KEY_PREF_THEME_MODE, (1 shl 30) - 1)
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

    fun isVibrancyEnabled(context: Context): Boolean {
        return true
    }

    fun useRoundSearchBar(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ROUND_SEARCH_BAR, false)
    }

    fun applyDarkTheme(activity: Activity) {
        migrateThemePref(activity)
        loadDarkThemePreference(activity)
        if (FeatureFlags.useDarkTheme)
            activity.setTheme(SETTINGS_THEMES[currentTheme])
    }

    fun enableBackportShortcuts(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_BACKPORT_SHORTCUTS, false)
    }

    fun showTopShadow(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_SHOW_TOP_SHADOW, true)
    }

    private val LAUNCHER_THEMES = intArrayOf(R.style.LauncherTheme, R.style.LauncherTheme_Dark, R.style.LauncherTheme_Black)
    private val SETTINGS_THEMES = intArrayOf(R.style.SettingsTheme, R.style.SettingsTheme_Dark, R.style.SettingsTheme_Black)

    fun hideHotseat(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HIDE_HOTSEAT, false)
    }

    fun planes(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PLANE, false)
    }

    fun showWeather(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_WEATHER, false)
    }

    fun enableEditing(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_EDITING, true)
    }

    fun animatedClockIcon(context: Context): Boolean {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ANIMATED_CLOCK_ICON, false)
    }
}
