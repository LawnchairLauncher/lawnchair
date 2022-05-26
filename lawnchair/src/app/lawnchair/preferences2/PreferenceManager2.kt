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
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.font.FontCache
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.preferencemanager.PreferenceManager
import com.patrykmichalik.preferencemanager.firstBlocking
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.lawnchair.preferences.PreferenceManager as LawnchairPreferenceManager
import com.android.launcher3.graphics.IconShape as L3IconShape

class PreferenceManager2(private val context: Context) : PreferenceManager {

    private val scope = MainScope()
    private val resourceProvider = DynamicResource.provider(context)

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
        defaultValue = context.resources.getBoolean(R.bool.config_default_dark_status_bar),
    )

    val hotseatQsb = preference(
        key = booleanPreferencesKey(name = "dock_search_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dock_search_bar),
        onSet = { reloadHelper.restart() },
    )

    val iconShape = preference(
        key = stringPreferencesKey(name = "icon_shape"),
        defaultValue = IconShape.fromString(context.getString(R.string.config_default_icon_shape)) ?: IconShape.Circle,
        parse = { IconShape.fromString(it) ?: IconShapeManager.getSystemIconShape(context) },
        save = { it.toString() },
    )

    val showNotificationCount = preference(
        key = booleanPreferencesKey(name = "show_notification_count"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_notification_count),
        onSet = { reloadHelper.reloadGrid() },
    )

    val themedHotseatQsb = preference(
        key = booleanPreferencesKey(name = "themed_hotseat_qsb"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_themed_hotseat_qsb),
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
        defaultValue = context.resources.getBoolean(R.bool.config_default_dock_search_bar_force_website),
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
        defaultValue = context.resources.getBoolean(R.bool.config_default_rounded_widgets),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showStatusBar = preference(
        key = booleanPreferencesKey(name = "show_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_status_bar),
    )

    val showTopShadow = preference(
        key = booleanPreferencesKey(name = "show_top_shadow"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_top_shadow),
    )

    val hideAppDrawerSearchBar = preference(
        key = booleanPreferencesKey(name = "hide_app_drawer_search_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_hide_app_drawer_search_bar),
        onSet = { reloadHelper.recreate() }
    )

    val enableFontSelection = preference(
        key = booleanPreferencesKey(name = "enable_font_selection"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_font_selection),
        onSet = { newValue ->
            if (!newValue) {
                val fontCache = FontCache.INSTANCE.get(context)
                LawnchairPreferenceManager.getInstance(context).fontWorkspace.set(newValue = fontCache.uiText)
            }
        },
    )

    val enableSmartspaceCalendarSelection = preference(
        key = booleanPreferencesKey(name = "enable_smartspace_calendar_selection"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_smartspace_calendar_selection),
    )

    val autoShowKeyboardInDrawer = preference(
        key = booleanPreferencesKey(name = "auto_show_keyboard_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_auto_show_keyboard_in_drawer),
    )

    val homeIconSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_size_factor),
        onSet = { reloadHelper.reloadIcons() },
    )

    val folderPreviewBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_preview_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_preview_background_opacity),
        onSet = { reloadHelper.reloadIcons() },
    )

    val showIconLabelsOnHomeScreen = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_size_factor),
        onSet = { reloadHelper.reloadIcons() },
    )

    val showIconLabelsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_in_drawer),
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerCellHeightFactor = preference(
        key = floatPreferencesKey(name = "drawer_cell_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_cell_height_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFuzzySearch = preference(
        key = booleanPreferencesKey(name = "enable_fuzzy_search"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_fuzzy_search),
    )

    val enableSmartspace = preference(
        key = booleanPreferencesKey(name = "enable_smartspace"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_smartspace),
        onSet = { reloadHelper.restart() },
    )

    val enableFeed = preference(
        key = booleanPreferencesKey(name = "enable_feed"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_feed),
        onSet = { reloadHelper.recreate() },
    )

    val showComponentNames = preference(
        key = booleanPreferencesKey(name = "show_component_names"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_component_names),
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

    val additionalFonts = preference(
        key = stringPreferencesKey(name = "additional_fonts"),
        defaultValue = "",
    )

    val enableTaskbarOnPhone = preference(
        key = booleanPreferencesKey("enable_taskbar_on_phone"),
        defaultValue = false,
        onSet = {
            reloadHelper.reloadGrid()
            reloadHelper.reloadTaskbar()
            reloadHelper.recreate()
        }
    )

    val smartspaceAagWidget = preference(
        key = booleanPreferencesKey("enable_smartspace_aag_widget"),
        defaultValue = true
    )

    val smartspaceBatteryStatus = preference(
        key = booleanPreferencesKey("enable_smartspace_battery_status"),
        defaultValue = true
    )

    val smartspaceNowPlaying = preference(
        key = booleanPreferencesKey("enable_smartspace_now_playing"),
        defaultValue = true
    )

    val smartspaceShowDate = preference(
        key = booleanPreferencesKey("smartspace_show_date"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_date),
    )

    val smartspaceShowTime = preference(
        key = booleanPreferencesKey("smartspace_show_time"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_time),
    )

    val smartspace24HourFormat = preference(
        key = booleanPreferencesKey("smartspace_24_hour_format"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_24_hour_format),
    )

    val smartspaceCalendar = preference(
        key = stringPreferencesKey(name = "smartspace_calendar"),
        defaultValue = SmartspaceCalendar.fromString(context.getString(R.string.config_default_smart_space_calendar)),
        parse = { SmartspaceCalendar.fromString(it) },
        save = { it.toString() },
    )

    val wallpaperDepthEffect = preference(
        key = booleanPreferencesKey(name = "enable_wallpaper_depth_effect"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() }
    )

    val doubleTapGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("double_tap_gesture_handler"),
        defaultValue = GestureHandlerConfig.Sleep
    )

    val swipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenAppDrawer
    )

    val swipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenNotifications
    )

    val homePressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("home_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp
    )

    val backPressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("back_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp
    )

    private inline fun <reified T> serializablePreference(
        key: Preferences.Key<String>,
        defaultValue: T
    ) = preference(
        key = key,
        defaultValue = defaultValue,
        parse = { Json.decodeFromString(it) },
        save = { Json.encodeToString(it) }
    )

    init {
        initializeIconShape(iconShape.firstBlocking())
        iconShape.get()
            .drop(1)
            .distinctUntilChanged()
            .onEach { shape ->
                initializeIconShape(shape)
                L3IconShape.init(context)
                LauncherAppState.getInstance(context).reloadIcons()
            }
            .launchIn(scope)
    }

    private fun initializeIconShape(shape: IconShape) {
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskId = shape.getHashString()
        CustomAdaptiveIconDrawable.sMask = shape.getMaskPath()
    }

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
