/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.preferences2

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lawnchair.theme.color.ColorMode

/**
 * Migrates the legacy boolean `dark_status_bar` preference to the tri-state
 * `status_bar_icon_mode` preference.
 *
 * The old boolean could only ever force dark icons on, because it was OR-ed with the
 * wallpaper-derived value. `true` therefore becomes [ColorMode.DARK], while `false` becomes
 * [ColorMode.AUTO] — which preserves the previous behaviour exactly, since a disabled switch
 * left the wallpaper hints in charge.
 *
 * The legacy key is intentionally left in place: [SharedPreferencesMigration] treats a missing
 * mapped key as "migration still pending", so removing it would re-trigger that migration on
 * every launch.
 */
class StatusBarIconModeMigration : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.contains(LEGACY_KEY) && !currentData.contains(NEW_KEY)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutablePreferences = currentData.toMutablePreferences()
        val wasDark = currentData[LEGACY_KEY] == true
        mutablePreferences[NEW_KEY] = if (wasDark) {
            ColorMode.DARK.toString()
        } else {
            ColorMode.AUTO.toString()
        }
        return mutablePreferences.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    companion object {
        private val LEGACY_KEY = booleanPreferencesKey("dark_status_bar")
        private val NEW_KEY = stringPreferencesKey("status_bar_icon_mode")
    }
}
