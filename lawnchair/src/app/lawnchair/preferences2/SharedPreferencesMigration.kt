/*
 * Copyright 2020 The Android Open Source Project
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
 *
 * Modifications copyright 2022 Lawnchair
 */

package app.lawnchair.preferences2

import android.content.Context
import androidx.datastore.migrations.SharedPreferencesView
import androidx.datastore.preferences.core.*
import com.android.launcher3.LauncherFiles

class SharedPreferencesMigration(private val context: Context) {

    private val sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY
    private val keys = mapOf(
        "pref_darkStatusBar" to "dark_status_bar", "pref_dockSearchBar" to "dock_search_bar",
        "pref_iconShape" to "icon_shape", "pref_themedHotseatQsb" to "themed_hotseat_qsb",
        "pref_accentColor2" to "accent_color", "hidden-app-set" to "hidden_apps",
        "pref_showStatusBar" to "show_status_bar", "pref_showSysUiScrim" to "show_top_shadow",
    )

    fun produceMigration() = androidx.datastore.migrations.SharedPreferencesMigration(
        context = context,
        sharedPreferencesName = sharedPreferencesName,
        keysToMigrate = keys.keys,
        shouldRunMigration = getShouldRunMigration(),
        migrate = produceMigrationFunction(),
    )

    private fun getShouldRunMigration(): suspend (Preferences) -> Boolean = { preferences ->
        val allKeys = preferences.asMap().keys.map { it.name }
        keys.values.any { it !in allKeys }
    }

    private fun produceMigrationFunction(): suspend (SharedPreferencesView, Preferences) -> Preferences =
        { sharedPreferences: SharedPreferencesView, currentData: Preferences ->
            val currentKeys = currentData.asMap().keys.map { it.name }
            val migratedKeys = currentKeys.map { currentKey -> keys.entries.first { entry -> entry.value == currentKey }.key }
            val filteredSharedPreferences = sharedPreferences.getAll().filter { (key, _) -> key !in migratedKeys }
            val mutablePreferences = currentData.toMutablePreferences()

            for ((key, value) in filteredSharedPreferences) {
                val newKey = keys[key] ?: key
                when (value) {
                    is Boolean -> mutablePreferences[booleanPreferencesKey(newKey)] = value
                    is Float -> mutablePreferences[floatPreferencesKey(newKey)] = value
                    is Int -> mutablePreferences[intPreferencesKey(newKey)] = value
                    is Long -> mutablePreferences[longPreferencesKey(newKey)] = value
                    is String -> mutablePreferences[stringPreferencesKey(newKey)] = value
                    is Set<*> -> { mutablePreferences[stringSetPreferencesKey(newKey)] = value as Set<String> }
                }
            }

            mutablePreferences.toPreferences()
        }
}
