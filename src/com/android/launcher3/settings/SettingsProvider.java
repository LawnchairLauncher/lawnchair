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

import java.util.Map;

public final class SettingsProvider {
    public static final String SETTINGS_KEY = "com.android.launcher3_preferences";

    public static final String SETTINGS_CHANGED = "settings_changed";

    public static final String SETTINGS_UI_HOMESCREEN_SEARCH = "ui_homescreen_search";

    private static Map<String, ?> sKeyValues;

    public static void load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SETTINGS_KEY, 0);
        sKeyValues = preferences.getAll();
    }

    public static int getInt(String key, int def) {
        return sKeyValues.containsKey(key) && sKeyValues.get(key) instanceof Integer ?
                (Integer) sKeyValues.get(key) : def;
    }

    public static int getInt(String key, Context context, int resource) {
        return getInt(key, context.getResources().getInteger(resource));
    }

    public static boolean getBoolean(String key, boolean def) {
        return sKeyValues.containsKey(key) && sKeyValues.get(key) instanceof Boolean ?
                (Boolean) sKeyValues.get(key) : def;
    }

    public static boolean getBoolean(String key, Context context, int resource) {
        return getBoolean(key, context.getResources().getBoolean(resource));
    }

    public static String getString(String key, String def) {
        return sKeyValues.containsKey(key) && sKeyValues.get(key) instanceof String ?
                (String) sKeyValues.get(key) : def;
    }

    public static String getString(String key, Context context, int resource) {
        return getString(key, context.getResources().getString(resource));
    }
}
