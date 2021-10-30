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
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.LawnchairLauncher
import app.lawnchair.font.FontCache
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.graphics.IconShape as L3IconShape

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

    val hiddenAppSet = StringSetPref("hidden-app-set", setOf())
    val iconPackPackage = StringPref("pref_iconPackPackage", "", reloadIcons)
    val allowRotation = BoolPref("pref_allowRotation", false)
    val wrapAdaptiveIcons = BoolPref("prefs_wrapAdaptive", false, reloadIcons)
    val addIconToHome = BoolPref("pref_add_icon_to_home", true)
    val enableHotseatQsb = BoolPref("pref_dockSearchBar", true, restart)
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
    val clearAllAsAction = BoolPref("pref_clearAllAsAction", false)
    val overrideWindowCornerRadius = BoolPref("pref_overrideWindowCornerRadius", false, recreate)
    val windowCornerRadius = IntPref("pref_windowCornerRadius", 80, recreate)
    val autoLaunchRoot = BoolPref("pref_autoLaunchRoot", false)
    val accentColor = ObjectPref(
        "pref_accentColor2",
        when {
            Utilities.ATLEAST_S -> ColorOption.SystemAccent
            Utilities.ATLEAST_O_MR1 -> ColorOption.WallpaperPrimary
            else -> ColorOption.LawnchairBlue
        },
        ColorOption::fromString,
        ColorOption::toString,
        recreate
    )
    val wallpaperScrolling = BoolPref("pref_wallpaperScrolling", true)
    val showSysUiScrim = BoolPref("pref_showSysUiScrim", true)
    val showStatusBar = BoolPref("pref_showStatusBar", true, recreate)
    val allAppsIconLabels = BoolPref("pref_allAppsIconLabels", true, reloadGrid)
    val searchAutoShowKeyboard = BoolPref("pref_searchAutoShowKeyboard", false)
    val enableDebugMenu = BoolPref("pref_enableDebugMenu", false)
    val folderPreviewBgOpacity = FloatPref("pref_folderPreviewBgOpacity", 1F, reloadIcons)
    val iconShape = ObjectPref(
        "pref_iconShape",
        IconShape.Circle,
        { IconShape.fromString(it) ?: IconShapeManager.getSystemIconShape(context) },
        { it.toString() },
        this::onIconShapeChanged
    )
    val customAppName = object : MutableMapPref<ComponentKey, String>("pref_appNameMap", reloadGrid) {
        override fun flattenKey(key: ComponentKey) = key.toString()
        override fun unflattenKey(key: String) = ComponentKey.fromString(key)!!
        override fun flattenValue(value: String) = value
        override fun unflattenValue(value: String) = value
    }
    val roundedWidgets = BoolPref("pref_roundedWidgets", true, reloadGrid)

    private val fontCache = FontCache.INSTANCE.get(context)
    val workspaceFont = FontPref("pref_workspaceFont", fontCache.uiText, recreate)

    val enableIconSelection = BoolPref("pref_enableIconSelection", false)
    val themedIcons = BoolPref("themed_icons", false)
    val hotseatQsbCornerRadius = FloatPref("pref_hotseatQsbCornerRadius", 1F, recreate)
    val themedHotseatQsb = BoolPref("pref_themedHotseatQsb", false)

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
        initializeIconShape()
    }

    // TODO: move these somewhere else
    private fun initializeIconShape() {
        val shape = iconShape.get()
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskId = shape.getHashString()
        CustomAdaptiveIconDrawable.sMask = shape.getMaskPath()
    }

    private fun onIconShapeChanged() {
        initializeIconShape()
        L3IconShape.init(context)
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
