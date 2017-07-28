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

package ch.deletescape.lawnchair.config;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;

/**
 * Defines a set of flags used to control various launcher behaviors
 */
public final class FeatureFlags {

    private static final String KEY_PREF_LIGHT_STATUS_BAR = "pref_forceLightStatusBar";
    private static final String KEY_PREF_PINCH_TO_OVERVIEW = "pref_pinchToOverview";
    private static final String KEY_PREF_PULLDOWN_NOTIS = "pref_pulldownNotis";
    private static final String KEY_PREF_HOTSEAT_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors";
    private static final String KEY_PREF_HAPTIC_FEEDBACK = "pref_enableHapticFeedback";
    private static final String KEY_PREF_KEEP_SCROLL_STATE = "pref_keepScrollState";
    private static final String KEY_FULL_WIDTH_SEARCHBAR = "pref_fullWidthSearchbar";
    private static final String KEY_SHOW_PIXEL_BAR = "pref_showPixelBar";
    private static final String KEY_HOME_OPENS_DRAWER = "pref_homeOpensDrawer";
    public static final String KEY_SHOW_VOICE_SEARCH_BUTTON = "pref_showMic";
    private static final String KEY_PREF_PIXEL_STYLE_ICONS = "pref_pixelStyleIcons";
    private static final String KEY_PREF_HIDE_APP_LABELS = "pref_hideAppLabels";
    private static final String KEY_PREF_ENABLE_SCREEN_ROTATION = "pref_enableScreenRotation";
    private static final String KEY_PREF_FULL_WIDTH_WIDGETS = "pref_fullWidthWidgets";
    private static final String KEY_PREF_SHOW_NOW_TAB = "pref_showGoogleNowTab";
    private static final String KEY_PREF_TRANSPARENT_HOTSEAT = "pref_isHotseatTransparent";
    private static final String KEY_PREF_ENABLE_DYNAMIC_UI = "pref_enableDynamicUi";
    private static final String KEY_PREF_ENABLE_BLUR = "pref_enableBlur";
    public static final String KEY_PREF_WHITE_GOOGLE_ICON = "pref_enableWhiteGoogleIcon";
    private static final String KEY_PREF_DARK_THEME = "pref_enableDarkTheme";
    private static final String KEY_PREF_ROUND_SEARCH_BAR = "pref_useRoundSearchBar";
    private static final String KEY_PREF_ENABLE_BACKPORT_SHORTCUTS = "pref_enableBackportShortcuts";
    private static final String KEY_PREF_SHOW_TOP_SHADOW = "pref_showTopShadow";
    public static final String KEY_PREF_THEME = "pref_theme";
    private static final String KEY_PREF_THEME_MODE = "pref_themeMode";
    private static final String KEY_PREF_HIDE_HOTSEAT = "pref_hideHotseat";
    private static final String KEY_PREF_PLANE = "pref_plane";
    private static final String KEY_PREF_WEATHER = "pref_weather";
    private static final String KEY_PREF_PULLDOWN_ACTION = "pref_pulldownAction";
    private static int darkThemeFlag;

    private FeatureFlags() {
    }

    public static boolean pinchToOverview(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PINCH_TO_OVERVIEW, true);
    }

    // When enabled the status bar may show dark icons based on the top of the wallpaper.
    public static boolean lightStatusBar(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_LIGHT_STATUS_BAR, false);
    }

    public static boolean hotseatShouldUseExtractedColors(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HOTSEAT_EXTRACTED_COLORS, true);
    }

    public static boolean enableHapticFeedback(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HAPTIC_FEEDBACK, false);
    }

    public static boolean keepScrollState(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_KEEP_SCROLL_STATE, false);
    }

    public static boolean useFullWidthSearchbar(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_FULL_WIDTH_SEARCHBAR, false);
    }

    public static boolean showVoiceSearchButton(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_SHOW_VOICE_SEARCH_BUTTON, false);
    }

    public static boolean showPixelBar(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_SHOW_PIXEL_BAR, true);
    }

    public static boolean homeOpensDrawer(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_HOME_OPENS_DRAWER, true);
    }

    public static boolean usePixelIcons(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PIXEL_STYLE_ICONS, true);
    }

    public static boolean enableScreenRotation(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_SCREEN_ROTATION, false);
    }

    public static boolean hideAppLabels(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HIDE_APP_LABELS, false);
    }

    public static boolean allowFullWidthWidgets(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_FULL_WIDTH_WIDGETS, false);
    }

    public static boolean showGoogleNowTab(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_SHOW_NOW_TAB, true);
    }

    public static boolean isTransparentHotseat(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_TRANSPARENT_HOTSEAT, false);
    }

    public static boolean isDynamicUiEnabled(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_DYNAMIC_UI, false);
    }

    public static boolean isBlurEnabled(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_BLUR, false);
    }

    public static boolean useWhiteGoogleIcon(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_WHITE_GOOGLE_ICON, false);
    }

    public static final int DARK_QSB = 1;
    public static final int DARK_FOLDER = 2;
    public static final int DARK_ALLAPPS = 4;
    public static final int DARK_SHORTCUTS = 8;
    public static final int DARK_BLUR = 16;

    public static int currentTheme;
    public static boolean useDarkTheme = true;

    public static void applyDarkThemePreference(Launcher launcher) {
        migrateThemePref(launcher);
        loadDarkThemePreference(launcher);
        if (useDarkTheme)
            launcher.setTheme(LAUNCHER_THEMES[currentTheme]);
    }

    private static void migrateThemePref(Context context) {
        boolean darkTheme = Utilities.getPrefs(context).getBoolean(KEY_PREF_DARK_THEME, false);
        if (darkTheme) {
            Utilities.getPrefs(context).edit()
                    .remove(KEY_PREF_DARK_THEME)
                    .putString(KEY_PREF_THEME, "1")
                    .apply();
        }
    }

    private static void migratePullDownPref(Context context) {
        boolean pulldownNotis = Utilities.getPrefs(context).getBoolean(KEY_PREF_PULLDOWN_NOTIS, true);
        if (!pulldownNotis) {
            Utilities.getPrefs(context).edit()
                    .remove(KEY_PREF_PULLDOWN_NOTIS)
                    .putString(KEY_PREF_PULLDOWN_ACTION, "0")
                    .apply();
        }
    }

    public static int pullDownAction(Context context) {
        migratePullDownPref(context);
        return Integer.parseInt(Utilities.getPrefs(context).getString(KEY_PREF_PULLDOWN_ACTION, "1"));
    }

    @SuppressWarnings("NumericOverflow")
    public static void loadDarkThemePreference(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        currentTheme = Integer.parseInt(prefs.getString(KEY_PREF_THEME, "0"));
        useDarkTheme = currentTheme != 0;
        darkThemeFlag = prefs.getInt(KEY_PREF_THEME_MODE, (1 << 30) - 1);
    }

    public static boolean useDarkTheme(int flag) {
        return useDarkTheme && (darkThemeFlag & flag) != 0;
    }

    public static Context applyDarkTheme(Context context, int flag) {
        if (useDarkTheme(flag)) {
            return new ContextThemeWrapper(context, LAUNCHER_THEMES[currentTheme]);
        } else {
            return context;
        }
    }

    public static boolean isVibrancyEnabled(Context context) {
        return true;
    }

    public static boolean useRoundSearchBar(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ROUND_SEARCH_BAR, false);
    }

    public static void applyDarkTheme(Activity activity) {
        migrateThemePref(activity);
        loadDarkThemePreference(activity);
        if (FeatureFlags.useDarkTheme)
            activity.setTheme(SETTINGS_THEMES[currentTheme]);
    }

    public static boolean enableBackportShortcuts(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_ENABLE_BACKPORT_SHORTCUTS, false);
    }

    public static boolean showTopShadow(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_SHOW_TOP_SHADOW, true);
    }

    private static int[] LAUNCHER_THEMES = {R.style.LauncherTheme, R.style.LauncherTheme_Dark, R.style.LauncherTheme_Black};
    private static int[] SETTINGS_THEMES = {R.style.SettingsTheme, R.style.SettingsTheme_Dark, R.style.SettingsTheme_Black};

    public static boolean hideHotseat(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_HIDE_HOTSEAT, false);
    }

    public static boolean planes(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_PLANE, false);
    }

    public static boolean showWeather(Context context) {
        return Utilities.getPrefs(context).getBoolean(KEY_PREF_WEATHER, false);
    }
}
