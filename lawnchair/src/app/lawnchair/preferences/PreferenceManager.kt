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

    private val restart = {
        reloadGrid()
        recreate()
    }

    val iconPackPackage = StringPref("pref_iconPackPackage", "", reloadIcons)
    val allowRotation = BoolPref("pref_allowRotation", false)
    val wrapAdaptiveIcons = BoolPref("prefs_wrapAdaptive", false, reloadIcons)
    val addIconToHome = BoolPref("pref_add_icon_to_home", true)
    val hotseatColumns = IdpIntPref("pref_hotseatColumns", { numHotseatIcons }, reloadGrid)
    val workspaceColumns = IdpIntPref("pref_workspaceColumns", { numColumns }, reloadGrid)
    val workspaceRows = IdpIntPref("pref_workspaceRows", { numRows }, reloadGrid)
    val folderColumns = IdpIntPref("pref_folderColumns", { numFolderColumns }, reloadGrid)
    val folderRows = IdpIntPref("pref_folderRows", { numFolderRows }, reloadGrid)
    val iconSizeFactor = FloatPref("pref_iconSizeFactor", 1F, reloadIcons)
    val textSizeFactor = FloatPref("pref_textSizeFactor", 1F, reloadGrid)
    val showHomeLabels = BoolPref("pref_showHomeLabels", true, reloadGrid)
    val allAppsIconSizeFactor = FloatPref("pref_allAppsIconSizeFactor", 1F, reloadIcons)
    val allAppsTextSizeFactor = FloatPref("pref_allAppsTextSizeFactor", 1F, reloadGrid)
    val allAppsColumns = IdpIntPref("pref_allAppsColumns", { numAllAppsColumns }, reloadGrid)
    val smartSpaceEnable = BoolPref("pref_smartSpaceEnable", true, restart)
    val minusOneEnable = BoolPref("pref_enableMinusOne", true, recreate)
    val useFuzzySearch = BoolPref("pref_useFuzzySearch", false)
    val hideAppSearchBar = BoolPref("pref_hideAppSearchBar", false, recreate)

    // TODO: Add the ability to manually delete empty pages.
    val allowEmptyPages = BoolPref("pref_allowEmptyPages", false)
    val drawerOpacity = FloatPref("pref_drawerOpacity", 1F, reloadGrid)
    val coloredBackgroundLightness = FloatPref("pref_coloredBackgroundLightness", 0.9F, reloadIcons)
    val feedProvider = StringPref("pref_feedProvider", "")
    val ignoreFeedWhitelist = BoolPref("pref_ignoreFeedWhitelist", false)
    val workspaceDt2s = BoolPref("pref_doubleTap2Sleep", true)
    val launcherTheme = StringPref("pref_launcherTheme", "system")
    val overrideWindowCornerRadius = BoolPref("pref_overrideWindowCornerRadius", false, recreate)
    val windowCornerRadius = IntPref("pref_windowCornerRadius", 80, recreate)
    val autoLaunchRoot = BoolPref("pref_autoLaunchRoot", false)
    val wallpaperScrolling = BoolPref("pref_wallpaperScrolling", true)
    val allAppsIconLabels = BoolPref("pref_allAppsIconLabels", true, reloadGrid)
    val searchAutoShowKeyboard = BoolPref("pref_searchAutoShowKeyboard", false)
    val enableDebugMenu = BoolPref("pref_enableDebugMenu", false)
    val folderPreviewBgOpacity = FloatPref("pref_folderPreviewBgOpacity", 1F, reloadIcons)
    val customAppName = object : MutableMapPref<ComponentKey, String>("pref_appNameMap", reloadGrid) {
        override fun flattenKey(key: ComponentKey) = key.toString()
        override fun unflattenKey(key: String) = ComponentKey.fromString(key)!!
        override fun flattenValue(value: String) = value
        override fun unflattenValue(value: String) = value
    }

    private val fontCache = FontCache.INSTANCE.get(context)
    val workspaceFont = FontPref("pref_workspaceFont", fontCache.uiText, recreate)

    val deviceSearch = BoolPref("device_search", true, recreate)
    val searchResultShortcuts = BoolPref("pref_searchResultShortcuts", true)
    val searchResultPeople = BoolPref("pref_searchResultPeople", true)
    val searchResultPixelTips = BoolPref("pref_searchResultPixelTips", false)
    val searchResultSettings = BoolPref("pref_searchResultSettings", false)

    val enableIconSelection = BoolPref("pref_enableIconSelection", false)
    val showComponentName = BoolPref("pref_showComponentName", false)
    val themedIcons = BoolPref("themed_icons", false)
    val hotseatQsbCornerRadius = FloatPref("pref_hotseatQsbCornerRadius", 1F, recreate)
    val allAppsCellHeightMultiplier = FloatPref("pref_allAppsCellHeightMultiplier", 1F, reloadGrid)

    val recentsActionScreenshot = BoolPref("pref_recentsActionScreenshot", !isOnePlusStock)
    val recentsActionShare = BoolPref("pref_recentsActionShare", isOnePlusStock)
    val recentsActionLens = BoolPref("pref_recentsActionLens", true)
    val recentsActionClearAll = BoolPref("pref_clearAllAsAction", false)
    val enableFontSelection = BoolPref("pref_enableFontSelection", true)

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::PreferenceManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}

@Composable
fun preferenceManager() = PreferenceManager.getInstance(LocalContext.current)
