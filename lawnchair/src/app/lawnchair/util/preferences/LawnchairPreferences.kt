/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair.util.preferences

import android.content.Context
import android.content.SharedPreferences
import app.lawnchair.LawnchairLauncher
import app.lawnchair.LawnchairLauncherQuickstep
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities

class LawnchairPreferences(val context: Context) {
    val listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs: SharedPreferences?, key: String? ->
            val las = LauncherAppState.getInstance(context)
            when (key) {
                ICON_PACK_PACKAGE, WRAP_ADAPTIVE_ICONS, COLORED_BACKGROUND_LIGHTNESS -> {
                    las.model.clearIconCache()
                    las.model.forceReload()
                }
                WORKSPACE_ROWS, WORKSPACE_COLUMNS, ALL_APPS_COLUMNS, FOLDER_ROWS, FOLDER_COLUMNS, HOTSEAT_COLUMNS -> {
                    // LauncherAppState.getInstance(context).invariantDeviceProfile.reInitGrid()
                    // LauncherAppState.getInstance(context).model.forceReload()
                    scheduleRestart()
                }
                TEXT_SIZE_FACTOR, ICON_SIZE_FACTOR, ALL_APPS_ICON_SIZE_FACTOR, ALL_APPS_TEXT_SIZE_FACTOR -> {
                    scheduleRestart()
                }
                DRAWER_OPACITY -> {
                    las.launcher.scrimView.refreshScrimAlpha(context)
                }
            }
        }

    private fun scheduleRestart() {
        if (BuildConfig.FLAVOR_recents == "withQuickstep") {
            LawnchairLauncherQuickstep.getLauncher(context).scheduleRestart()
        } else {
            LawnchairLauncher.getLauncher(context).scheduleRestart()
        }
    }

    companion object {

        private var INSTANCE: SharedPreferences? = null

        @kotlin.jvm.JvmField
        var ICON_PACK_PACKAGE: String = "pref_iconPackPackage"

        @kotlin.jvm.JvmField
        var HOTSEAT_COLUMNS: String = "pref_hotseatColumns"

        @kotlin.jvm.JvmField
        var WORKSPACE_COLUMNS: String = "pref_workspaceColumns"

        @kotlin.jvm.JvmField
        var WORKSPACE_ROWS: String = "pref_workspaceRows"

        @kotlin.jvm.JvmField
        var ALL_APPS_COLUMNS: String = "pref_allAppsColumns"

        @kotlin.jvm.JvmField
        var FOLDER_COLUMNS: String = "pref_folderColumns"

        @kotlin.jvm.JvmField
        var FOLDER_ROWS: String = "pref_folderRows"

        @kotlin.jvm.JvmField
        var ICON_SIZE_FACTOR: String = "pref_iconSizeFactor"

        @kotlin.jvm.JvmField
        var TEXT_SIZE_FACTOR: String = "pref_textSizeFactor"

        @kotlin.jvm.JvmField
        var ALL_APPS_ICON_SIZE_FACTOR: String = "pref_allAppsIconSizeFactor"

        @kotlin.jvm.JvmField
        var ALL_APPS_TEXT_SIZE_FACTOR: String = "pref_allAppsTextSizeFactor"

        @kotlin.jvm.JvmField
        var WRAP_ADAPTIVE_ICONS: String = "prefs_wrapAdaptive"

        // TODO: Add the ability to manually delete empty pages.
        @kotlin.jvm.JvmField
        var ALLOW_EMPTY_PAGES: String = "pref_allowEmptyPages"

        @kotlin.jvm.JvmField
        var IGNORE_FEED_WHITELIST: String = "pref_ignoreFeedWhitelist"

        @kotlin.jvm.JvmField
        var FEED_PROVIDER: String = "pref_feedProvider"

        @kotlin.jvm.JvmField
        var ENABLE_MINUS_ONE: String = "pref_enableMinusOne"

        @kotlin.jvm.JvmField
        var DRAWER_OPACITY: String = "pref_drawerOpacity"

        @kotlin.jvm.JvmField
        var COLORED_BACKGROUND_LIGHTNESS: String = "pref_coloredBackgroundLightness"

        fun getInstance(context: Context?): SharedPreferences? = when {
            context == null -> null
            INSTANCE == null -> Utilities.getPrefs(context)
            else -> INSTANCE
        }
    }
}