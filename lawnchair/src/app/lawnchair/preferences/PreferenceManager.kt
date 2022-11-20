/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.LawnchairLauncher
import app.lawnchair.font.FontCache
import app.lawnchair.util.isOnePlusStock
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject

class PreferenceManager private constructor(private val context: Context) : BasePreferenceManager(context) {
    private val idp get() = InvariantDeviceProfile.INSTANCE.get(context)
    private val reloadIcons = { idp.onPreferencesChanged(context) }
    private val reloadGrid: () -> Unit = { idp.onPreferencesChanged(context) }

    private val recreate = {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
        Unit
    }

    val iconPackPackage = StringPref("pref_iconPackPackage", "", reloadIcons)
    val allowRotation = BoolPref("pref_allowRotation", false)
    val wrapAdaptiveIcons = BoolPref("prefs_wrapAdaptive", false, reloadIcons)
    val transparentIconBackground = BoolPref("prefs_transparentIconBackground", false, reloadIcons)
    val addIconToHome = BoolPref("pref_add_icon_to_home", true)
    val hotseatColumns = IntPref("pref_hotseatColumns", 4, reloadGrid)
    val workspaceColumns = IntPref("pref_workspaceColumns", 4)
    val workspaceRows = IntPref("pref_workspaceRows", 5)
    val workspaceIncreaseMaxGridSize = BoolPref("pref_workspace_increase_max_grid_size", false)
    val folderRows = IdpIntPref("pref_folderRows", { numFolderRows }, reloadGrid)

    val drawerOpacity = FloatPref("pref_drawerOpacity", 1F, recreate)
    val coloredBackgroundLightness = FloatPref("pref_coloredBackgroundLightness", 0.9F, reloadIcons)
    val feedProvider = StringPref("pref_feedProvider", "")
    val launcherTheme = StringPref("pref_launcherTheme", "system")
    val overrideWindowCornerRadius = BoolPref("pref_overrideWindowCornerRadius", false, recreate)
    val windowCornerRadius = IntPref("pref_windowCornerRadius", 80, recreate)
    val autoLaunchRoot = BoolPref("pref_autoLaunchRoot", false)
    val wallpaperScrolling = BoolPref("pref_wallpaperScrolling", true)
    val enableDebugMenu = BoolPref("pref_enableDebugMenu", false)
    val customAppName = object : MutableMapPref<ComponentKey, String>("pref_appNameMap", reloadGrid) {
        override fun flattenKey(key: ComponentKey) = key.toString()
        override fun unflattenKey(key: String) = ComponentKey.fromString(key)!!
        override fun flattenValue(value: String) = value
        override fun unflattenValue(value: String) = value
    }

    private val fontCache = FontCache.INSTANCE.get(context)
    val fontWorkspace = FontPref("pref_workspaceFont", fontCache.uiText, recreate)
    val fontHeading = FontPref("pref_fontHeading", fontCache.uiRegular, recreate)
    val fontHeadingMedium = FontPref("pref_fontHeadingMedium", fontCache.uiMedium, recreate)
    val fontBody = FontPref("pref_fontBody", fontCache.uiText, recreate)
    val fontBodyMedium = FontPref("pref_fontBodyMedium", fontCache.uiTextMedium, recreate)

    val deviceSearch = BoolPref("device_search", true, recreate)
    val searchResultShortcuts = BoolPref("pref_searchResultShortcuts", true)
    val searchResultPeople = BoolPref("pref_searchResultPeople", true)
    val searchResultPixelTips = BoolPref("pref_searchResultPixelTips", false)
    val searchResultSettings = BoolPref("pref_searchResultSettings", false)

    val themedIcons = BoolPref("themed_icons", false)
    val drawerThemedIcons = BoolPref("drawer_themed_icons", false, recreate)
    val hotseatQsbCornerRadius = FloatPref("pref_hotseatQsbCornerRadius", 1F, recreate)

    val recentsActionScreenshot = BoolPref("pref_recentsActionScreenshot", !isOnePlusStock)
    val recentsActionShare = BoolPref("pref_recentsActionShare", isOnePlusStock)
    val recentsActionLens = BoolPref("pref_recentsActionLens", true)
    val recentsActionClearAll = BoolPref("pref_clearAllAsAction", false)
    val recentsTranslucentBackground = BoolPref("pref_recentsTranslucentBackground", false, recreate)

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
        migratePrefs(CURRENT_VERSION) { oldVersion ->
            if (oldVersion < 2) {
                val gridState = DeviceGridState(context).toProtoMessage()
                if (gridState.hotseatCount != -1) {
                    val colsAndRows = gridState.gridSize.split(",")
                    workspaceColumns.set(colsAndRows[0].toInt())
                    workspaceRows.set(colsAndRows[1].toInt())
                    hotseatColumns.set(gridState.hotseatCount)
                }
            }
        }
    }

    companion object {
        private const val CURRENT_VERSION = 2

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::PreferenceManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}

@Composable
fun preferenceManager() = PreferenceManager.getInstance(LocalContext.current)
