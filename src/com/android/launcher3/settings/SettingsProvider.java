/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.launcher3.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsProvider {
    public static final String SETTINGS_KEY = "trebuchet_preferences";

    public static final String SETTINGS_CHANGED = "settings_changed";

    public static final String SETTINGS_UI_HOMESCREEN_DEFAULT_SCREEN_ID = "ui_homescreen_default_screen_id";
    public static final String SETTINGS_UI_HOMESCREEN_SEARCH = "ui_homescreen_search";
    public static final String SETTINGS_UI_HOMESCREEN_HIDE_ICON_LABELS = "ui_homescreen_general_hide_icon_labels";
    public static final String SETTINGS_UI_HOMESCREEN_SCROLLING_TRANSITION_EFFECT = "ui_homescreen_scrolling_transition_effect";
    public static final String SETTINGS_UI_HOMESCREEN_SCROLLING_WALLPAPER_SCROLL = "ui_homescreen_scrolling_wallpaper_scroll";
    public static final String SETTINGS_UI_HOMESCREEN_SCROLLING_PAGE_OUTLINES = "ui_homescreen_scrolling_page_outlines";
    public static final String SETTINGS_UI_HOMESCREEN_SCROLLING_FADE_ADJACENT = "ui_homescreen_scrolling_fade_adjacent";
    public static final String SETTINGS_UI_DRAWER_SCROLLING_TRANSITION_EFFECT = "ui_drawer_scrolling_transition_effect";
    public static final String SETTINGS_UI_DRAWER_SCROLLING_FADE_ADJACENT = "ui_drawer_scrolling_fade_adjacent";
    public static final String SETTINGS_UI_DRAWER_HIDDEN_APPS = "ui_drawer_hidden_apps";
    public static final String SETTINGS_UI_DRAWER_REMOVE_HIDDEN_APPS_SHORTCUTS = "ui_drawer_remove_hidden_apps_shortcuts";
    public static final String SETTINGS_UI_DRAWER_REMOVE_HIDDEN_APPS_WIDGETS = "ui_drawer_remove_hidden_apps_widgets";
    public static final String SETTINGS_UI_DRAWER_HIDE_ICON_LABELS = "ui_drawer_hide_icon_labels";
    public static final String SETTINGS_UI_GENERAL_ICONS_LARGE = "ui_general_icons_large";
    public static final String SETTINGS_UI_GENERAL_ICONS_TEXT_FONT_FAMILY = "ui_general_icons_text_font";
    public static final String SETTINGS_UI_GENERAL_ICONS_TEXT_FONT_STYLE = "ui_general_icons_text_font_style";
    public static final String SETTINGS_UI_GENERAL_ICONS_ICON_PACK = "ui_general_iconpack";

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(SETTINGS_KEY, Context.MODE_MULTI_PROCESS);
    }

    public static int getIntCustomDefault(Context context, String key, int def) {
        return get(context).getInt(key, def);
    }

    public static int getInt(Context context, String key, int resource) {
        return getIntCustomDefault(context, key, context.getResources().getInteger(resource));
    }

    public static long getLongCustomDefault(Context context, String key, long def) {
        return get(context).getLong(key, def);
    }

    public static long getLong(Context context, String key, int resource) {
        return getLongCustomDefault(context, key, context.getResources().getInteger(resource));
    }

    public static boolean getBooleanCustomDefault(Context context, String key, boolean def) {
        return get(context).getBoolean(key, def);
    }

    public static boolean getBoolean(Context context, String key, int resource) {
        return getBooleanCustomDefault(context, key, context.getResources().getBoolean(resource));
    }

    public static String getStringCustomDefault(Context context, String key, String def) {
        return get(context).getString(key, def);
    }

    public static String getString(Context context, String key, int resource) {
        return getStringCustomDefault(context, key, context.getResources().getString(resource));
    }

    public static void putString(Context context, String key, String value) {
        get(context).edit().putString(key, value).commit();
    }
}
