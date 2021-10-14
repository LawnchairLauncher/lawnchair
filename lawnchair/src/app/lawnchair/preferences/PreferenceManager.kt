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

package app.lawnchair.preferences

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.LawnchairLauncher
import app.lawnchair.font.FontCache
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.ui.theme.LAWNCHAIR_BLUE
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.IconShape
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.util.MainThreadInitializedObject

class PreferenceManager private constructor(private val context: Context) : BasePreferenceManager(context) {
    private val idp get() = InvariantDeviceProfile.INSTANCE.get(context)
    private val reloadIcons = { idp.onPreferencesChanged(context) }
    private val reloadGrid: () -> Unit = { idp.onPreferencesChanged(context) }

    private val scheduleRestart = {
        LawnchairLauncher.instance?.scheduleRestart()
        Unit
    }

    private val recreate = {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
        Unit
    }

    val hiddenAppSet = StringSetPref("hidden-app-set", setOf())
    val iconPackPackage = StringPref("pref_iconPackPackage", "", reloadIcons)
    val allowRotation = BoolPref("pref_allowRotation", false)
    val wrapAdaptiveIcons = BoolPref("prefs_wrapAdaptive", false, reloadIcons)
    val addIconToHome = BoolPref("pref_add_icon_to_home", true)
    val enableHotseatQsb = BoolPref("pref_dockSearchBar", true) {
        reloadGrid()
        recreate()
    }
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
    val smartSpaceEnable = BoolPref("pref_smartSpaceEnable", true, scheduleRestart)
    val minusOneEnable = BoolPref("pref_enableMinusOne", true, recreate)
    val useFuzzySearch = BoolPref("pref_useFuzzySearch", false)

    // TODO: Add the ability to manually delete empty pages.
    val allowEmptyPages = BoolPref("pref_allowEmptyPages", false)
    val drawerOpacity = FloatPref("pref_drawerOpacity", 1F, reloadGrid)
    val coloredBackgroundLightness = FloatPref("pref_coloredBackgroundLightness", 0.9F, reloadIcons)
    val feedProvider = StringPref("pref_feedProvider", "")
    val ignoreFeedWhitelist = BoolPref("pref_ignoreFeedWhitelist", false)
    val workspaceDt2s = BoolPref("pref_doubleTap2Sleep", true)
    val launcherTheme = StringPref("pref_launcherTheme", "system")
    val clearAllAsAction = BoolPref("pref_clearAllAsAction", false)
    val overrideWindowCornerRadius = BoolPref("pref_overrideWindowCornerRadius", false, recreate)
    val windowCornerRadius = IntPref("pref_windowCornerRadius", 80, recreate)
    val autoLaunchRoot = BoolPref("pref_autoLaunchRoot", false)
    val useSystemAccent = BoolPref("pref_useSystemAccent", Utilities.ATLEAST_S, recreate)
    val accentColor = IntPref("pref_accentColor", LAWNCHAIR_BLUE.toInt(), recreate)
    val wallpaperScrolling = BoolPref("pref_wallpaperScrolling", true)
    val showSysUiScrim = BoolPref("pref_showSysUiScrim", true)
    val showStatusBar = BoolPref("pref_showStatusBar", true, recreate)
    val allAppsIconLabels = BoolPref("pref_allAppsIconLabels", true, reloadGrid)
    val searchAutoShowKeyboard = BoolPref("pref_searchAutoShowKeyboard", false)
    val enableDebugMenu = BoolPref("pref_enableDebugMenu", false)
    val customIconShape = StringPref("pref_customIconShape", "", this::onIconShapeChanged)
    val folderPreviewBgOpacity = FloatPref("pref_folderPreviewBgOpacity", 1F, reloadIcons)

    private val fontCache = FontCache.INSTANCE.get(context)
    val workspaceFont = FontPref("pref_workspaceFont", fontCache.uiTextMedium, recreate)

    private val maskPathMap = mapOf(
        "circle" to "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z",
        "roundedRect" to "M50,0L88,0 C94.4,0 100,5.4 100 12 L100,88 C100,94.6 94.6 100 88 100 L12,100 C5.4,100 0,94.6 0,88 L0 12 C0 5.4 5.4 0 12 0 L50,0 Z",
        "squircle" to "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z",
        "pebble" to "MM55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z",
    )

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
        initializeIconShape()
    }

    // TODO: move these somewhere else
    private fun initializeIconShape() {
        var maskPath = maskPathMap[customIconShape.get()]
        if (maskPath == null) {
            val resId = IconProvider.CONFIG_ICON_MASK_RES_ID
            if (resId != 0) {
                maskPath = context.getString(resId)
            }
        }
        if (maskPath == null) {
            maskPath = maskPathMap["circle"]
        }
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskPath = maskPath
    }

    private fun onIconShapeChanged() {
        initializeIconShape()
        IconShape.init(context)
        idp.onPreferencesChanged(context)
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
