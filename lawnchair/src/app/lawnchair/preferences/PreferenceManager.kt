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
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.GridSizeMigrationTaskV2
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.MainThreadInitializedObject

class PreferenceManager private constructor(private val context: Context) : BasePreferenceManager(context) {
    private val idp get() = InvariantDeviceProfile.INSTANCE.get(context)
    private val reloadIcons = { idp.onPreferencesChanged(context, InvariantDeviceProfile.CHANGE_FLAG_ICON_PARAMS) }
    private val reloadGrid: () -> Unit = {
        val defaultGrid = idp.closestProfile
        val numColumns = workspaceColumns.get(defaultGrid)
        val numRows = workspaceRows.get(defaultGrid)
        val numHotseatIcons = hotseatColumns.get(defaultGrid)
        if (GridSizeMigrationTaskV2.needsToMigrate(context, numColumns, numRows, numHotseatIcons)) {
            toggleCurrentDbSlot()
        }
        idp.onPreferencesChanged(context, InvariantDeviceProfile.CHANGE_FLAG_GRID)
    }

    private val scheduleRestart = {
        LawnchairLauncher.instance?.scheduleRestart()
        Unit
    }

    val hiddenAppSet = StringSetPref("hidden-app-set", setOf())
    val iconPackPackage = StringPref("pref_iconPackPackage", "", reloadIcons)
    val allowRotation = BoolPref("pref_allowRotation", RotationHelper.getAllowRotationDefaultValue())
    val wrapAdaptiveIcons = BoolPref("prefs_wrapAdaptive", false, reloadIcons)
    val addIconToHome = BoolPref("pref_add_icon_to_home", true)
    val enableHotseatQsb = BoolPref("pref_dockSearchBar", true, reloadGrid)
    val hotseatColumns = IdpIntPref("pref_hotseatColumns", { numHotseatIcons }, reloadGrid)
    val workspaceColumns = IdpIntPref("pref_workspaceColumns", { numColumns }, reloadGrid)
    val workspaceRows = IdpIntPref("pref_workspaceRows", { numRows }, reloadGrid)
    val folderColumns = IdpIntPref("pref_folderColumns", { numFolderColumns }, reloadGrid)
    val folderRows = IdpIntPref("pref_folderRows", { numFolderRows }, reloadGrid)
    val iconSizeFactor = FloatPref("pref_iconSizeFactor", 1F, scheduleRestart)
    val textSizeFactor = FloatPref("pref_textSizeFactor", 1F, scheduleRestart)
    val allAppsIconSizeFactor = FloatPref("pref_allAppsIconSizeFactor", 1F, scheduleRestart)
    val allAppsTextSizeFactor = FloatPref("pref_allAppsTextSizeFactor", 1F, scheduleRestart)
    val allAppsColumns = IdpIntPref("pref_allAppsColumns", { numAllAppsColumns }, reloadGrid)
    val smartSpaceEnable = BoolPref("pref_smartSpaceEnable", true, scheduleRestart)
    val minusOneEnable = BoolPref("pref_enableMinusOne", true)
    val useFuzzySearch = BoolPref("pref_useFuzzySearch", false)

    // TODO: Add the ability to manually delete empty pages.
    val allowEmptyPages = BoolPref("pref_allowEmptyPages", false)
    val drawerOpacity = FloatPref("pref_drawerOpacity", 1F) {
        LauncherAppState.getInstance(context).launcher.scrimView.refreshScrimAlpha(context)
    }
    val coloredBackgroundLightness = FloatPref("pref_coloredBackgroundLightness", 0.9F, reloadIcons)
    val feedProvider = StringPref("pref_feedProvider", "")
    val ignoreFeedWhitelist = BoolPref("pref_ignoreFeedWhitelist", false)
    val workspaceDt2s = BoolPref("pref_doubleTap2Sleep", true)

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    val currentDbSlot = StringPref("pref_currentDbSlot", "a")
    private fun toggleCurrentDbSlot() {
        if (currentDbSlot.get() == "a") {
            currentDbSlot.set("b")
        } else {
            currentDbSlot.set("a")
        }
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::PreferenceManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}

@Composable
fun preferenceManager(): PreferenceManager {
    return PreferenceManager.getInstance(LocalContext.current)
}
