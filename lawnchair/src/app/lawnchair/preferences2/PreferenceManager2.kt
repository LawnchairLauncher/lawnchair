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

package app.lawnchair.preferences2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.font.FontCache
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.preferencemanager.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.lawnchair.preferences.PreferenceManager as LawnchairPreferenceManager

class PreferenceManager2(private val context: Context) : PreferenceManager {

    private fun idpPreference(
        key: Preferences.Key<Int>,
        defaultSelector: InvariantDeviceProfile.GridOption.() -> Int,
        onSet: (Int) -> Unit = {},
    ): IdpPreference = IdpPreference(
        key = key,
        defaultSelector = defaultSelector,
        preferencesDataStore = preferencesDataStore,
        onSet = onSet,
    )

    override val preferencesDataStore = context.preferencesDataStore
    private val reloadHelper = ReloadHelper(context)

    val darkStatusBar = preference(
        key = booleanPreferencesKey(name = "dark_status_bar"),
        defaultValue = false,
    )

    val hotseatQsb = preference(
        key = booleanPreferencesKey(name = "dock_search_bar"),
        defaultValue = true,
        onSet = { reloadHelper.restart() },
    )

    val iconShape = preference(
        key = stringPreferencesKey(name = "icon_shape"),
        defaultValue = IconShape.Circle,
        parse = { IconShape.fromString(it) ?: IconShapeManager.getSystemIconShape(context) },
        save = { it.toString() },
    )

    val themedHotseatQsb = preference(
        key = booleanPreferencesKey(name = "themed_hotseat_qsb"),
        defaultValue = false,
    )

    val hotseatQsbProvider = preference(
        key = stringPreferencesKey(name = "dock_search_bar_provider"),
        defaultValue = QsbSearchProvider.resolveDefault(context),
        parse = { QsbSearchProvider.fromId(it) },
        save = { it.id },
        onSet = { reloadHelper.recreate() }
    )

    val hotseatQsbForceWebsite = preference(
        key = booleanPreferencesKey(name = "dock_search_bar_force_website"),
        defaultValue = false,
    )

    val accentColor = preference(
        key = stringPreferencesKey(name = "accent_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = when {
            Utilities.ATLEAST_S -> ColorOption.SystemAccent
            Utilities.ATLEAST_O_MR1 -> ColorOption.WallpaperPrimary
            else -> ColorOption.LawnchairBlue
        },
    )

    val hiddenApps = preference(
        key = stringSetPreferencesKey(name = "hidden_apps"),
        defaultValue = setOf(),
    )

    val roundedWidgets = preference(
        key = booleanPreferencesKey(name = "rounded_widgets"),
        defaultValue = true,
        onSet = { reloadHelper.reloadGrid() },
    )

    val showStatusBar = preference(
        key = booleanPreferencesKey(name = "show_status_bar"),
        defaultValue = true,
    )

    val showTopShadow = preference(
        key = booleanPreferencesKey(name = "show_top_shadow"),
        defaultValue = true,
    )

    val hideAppDrawerSearchBar = preference(
        key = booleanPreferencesKey(name = "hide_app_drawer_search_bar"),
        defaultValue = false,
    )

    val enableFontSelection = preference(
        key = booleanPreferencesKey(name = "enable_font_selection"),
        defaultValue = true,
        onSet = { newValue ->
            if (!newValue) {
                val fontCache = FontCache.INSTANCE.get(context)
                LawnchairPreferenceManager.getInstance(context).workspaceFont.set(newValue = fontCache.uiText)
            }
        },
    )

    val dt2s = preference(
        key = booleanPreferencesKey(name = "dt2s"),
        defaultValue = true,
    )

    val autoShowKeyboardInDrawer = preference(
        key = booleanPreferencesKey(name = "auto_show_keyboard_in_drawer"),
        defaultValue = false,
    )

    val homeIconSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_size_factor"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadIcons() },
    )

    val folderPreviewBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_preview_background_opacity"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadIcons() },
    )

    val showIconLabelsOnHomeScreen = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen"),
        defaultValue = true,
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_size_factor"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadIcons() },
    )

    val showIconLabelsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_in_drawer"),
        defaultValue = true,
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_size_factor"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_label_size_factor"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerCellHeightFactor = preference(
        key = floatPreferencesKey(name = "drawer_cell_height_factor"),
        defaultValue = 1F,
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFuzzySearch = preference(
        key = booleanPreferencesKey(name = "enable_fuzzy_search"),
        defaultValue = false,
    )

    val enableSmartspace = preference(
        key = booleanPreferencesKey(name = "enable_smartspace"),
        defaultValue = true,
        onSet = { reloadHelper.restart() },
    )

    val enableFeed = preference(
        key = booleanPreferencesKey(name = "enable_feed"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val enableIconSelection = preference(
        key = booleanPreferencesKey(name = "enable_icon_selection"),
        defaultValue = false,
        onSet = {
            if (!it) {
                val iconOverrideRepository = IconOverrideRepository.INSTANCE.get(context)
                CoroutineScope(Dispatchers.IO).launch {
                    iconOverrideRepository.deleteAll()
                }
            }
        }
    )

    val showComponentNames = preference(
        key = booleanPreferencesKey(name = "show_component_names"),
        defaultValue = false,
    )

    val drawerColumns = idpPreference(
        key = intPreferencesKey(name = "drawer_columns"),
        defaultSelector = { numAllAppsColumns },
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderColumns = idpPreference(
        key = intPreferencesKey(name = "folder_columns"),
        defaultSelector = { numFolderColumns },
        onSet = { reloadHelper.reloadGrid() },
    )

    companion object {
        private val Context.preferencesDataStore by preferencesDataStore(
            name = "preferences",
            produceMigrations = { listOf(SharedPreferencesMigration(context = it).produceMigration()) },
        )

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::PreferenceManager2)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}

@Composable
fun preferenceManager2() = PreferenceManager2.getInstance(LocalContext.current)
