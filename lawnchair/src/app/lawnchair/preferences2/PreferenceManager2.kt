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
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.data.Converters
import app.lawnchair.font.FontCache
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.PreferenceManager as LawnchairPreferenceManager
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.WebSearchProvider
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.toOptionOrderString
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import app.lawnchair.ui.preferences.data.liveinfo.LiveInformationManager
import app.lawnchair.util.kotlinxJson
import app.lawnchair.views.overlay.FullScreenOverlayMode
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.graphics.IconShape as L3IconShape
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.PreferenceManager
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class PreferenceManager2 private constructor(private val context: Context) :
    PreferenceManager,
    SafeCloseable {

    private val scope = MainScope()
    private val resourceProvider = DynamicResource.provider(context)
    private var liveInformationManager: LiveInformationManager =
        LiveInformationManager.getInstance(context)

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

    val hotseatMode = preference(
        key = stringPreferencesKey("hotseat_mode"),
        defaultValue = HotseatMode.fromString(context.getString(R.string.config_default_hotseat_mode)),
        parse = { HotseatMode.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.restart() },
    )

    val iconShape = preference(
        key = stringPreferencesKey(name = "icon_shape"),
        defaultValue = IconShape.fromString(
            value = context.getString(R.string.config_default_icon_shape),
            context = context,
        ) ?: IconShape.Circle,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
    )

    val customIconShape = preference(
        key = stringPreferencesKey(name = "custom_icon_shape"),
        defaultValue = null,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
        onSet = { it?.let(iconShape::setBlocking) },
    )

    val alwaysReloadIcons = preference(
        key = booleanPreferencesKey(name = "always_reload_icons"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_always_reload_icons),
    )

    val colorStyle = preference(
        key = stringPreferencesKey("color_style"),
        defaultValue = ColorStyle.fromString("tonal_spot"),
        parse = ColorStyle::fromString,
        save = ColorStyle::toString,
        onSet = { reloadHelper.restart() },
    )

    val strokeColorStyle = preference(
        key = stringPreferencesKey(name = "stroke_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.restart() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hotseatBackgroundColor = preference(
        key = stringPreferencesKey(name = "hotseat_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_hotseat_bg_color)),
    )

    val appDrawerBackgroundColor = preference(
        key = stringPreferencesKey(name = "app_drawer_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_app_drawer_bg_color)),
    )

    val appDrawerSearchBarBackground = preference(
        key = booleanPreferencesKey(name = "all_apps_search_bar_background"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_search_bar_background),
        onSet = { reloadHelper.recreate() },
    )

    val notificationDotColor = preference(
        key = stringPreferencesKey(name = "notification_dot_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_color)),
    )

    val notificationDotTextColor = preference(
        key = stringPreferencesKey(name = "notification_dot_text_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_text_color)),
    )

    val folderColor = preference(
        key = stringPreferencesKey(name = "folder_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_folder_color)),
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

    val isHotseatEnabled = preference(
        key = booleanPreferencesKey(name = "pref_show_hotseat"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_hotseat),
        onSet = {
            reloadHelper.recreate()
            reloadHelper.reloadGrid()
        },
    )

    val hotseatQsbProvider = preference(
        key = stringPreferencesKey(name = "dock_search_bar_provider"),
        defaultValue = getRemoteDefault("dock_search_bar_provider")?.let {
            QsbSearchProvider.fromId(it)
        } ?: QsbSearchProvider.resolveDefault(context),
        parse = { QsbSearchProvider.fromId(it) },
        save = { it.id },
        onSet = { reloadHelper.recreate() },
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
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hiddenApps = preference(
        key = stringSetPreferencesKey(name = "hidden_apps"),
        defaultValue = setOf(),
        onSet = { reloadHelper.reloadGrid() },
    )

    val roundedWidgets = preference(
        key = booleanPreferencesKey(name = "rounded_widgets"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_rounded_widgets),
        onSet = { reloadHelper.reloadGrid() },
    )

    val allowWidgetOverlap = preference(
        key = booleanPreferencesKey(name = "allow_widget_overlap"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_allow_widget_overlap),
        onSet = { reloadHelper.reloadGrid() },
    )

    val forceWidgetResize = preference(
        key = booleanPreferencesKey(name = "force_widget_resize"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_force_widget_resize),
    )

    val widgetUnlimitedSize = preference(
        key = booleanPreferencesKey(name = "widget_unlimited_size"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_widget_unlimited_size),
    )

    val showStatusBar = preference(
        key = booleanPreferencesKey(name = "show_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_status_bar),
    )

    val statusBarClock = preference(
        key = booleanPreferencesKey(name = "status_bar_clock"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dynamic_hide_status_bar_clock),
        onSet = { reloadHelper.reloadGrid() },
    )

    val rememberPosition = preference(
        key = booleanPreferencesKey(name = "all_apps_remember_position"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_remember_position),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showScrollbar = preference(
        key = booleanPreferencesKey(name = "all_apps_show_scrollbar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_scrollbar),
        onSet = { reloadHelper.recreate() },
    )

    val showTopShadow = preference(
        key = booleanPreferencesKey(name = "show_top_shadow"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_top_shadow),
    )

    val lockHomeScreen = preference(
        key = booleanPreferencesKey(name = "lock_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen),
    )

    val legacyPopupOptionsMigrated = preference(
        key = booleanPreferencesKey(name = "legacy_popup_options_migrated"),
        defaultValue = false,
    )

    val launcherPopupOrder = preference(
        key = stringPreferencesKey(name = "launcher_popup_order"),
        defaultValue = LauncherOptionsPopup.DEFAULT_ORDER.toOptionOrderString(),
        onSet = { reloadHelper.reloadGrid() },
    )

    val lockHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "lock_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val editHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "edit_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_edit_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showSystemSettingsEntryOnPopUp = preference(
        key = booleanPreferencesKey(name = "show_system_settings_entry_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_system_settings_entry_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hideAppDrawerSearchBar = preference(
        key = booleanPreferencesKey(name = "hide_app_drawer_search_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_hide_app_drawer_search_bar),
        onSet = { reloadHelper.recreate() },
    )

    val showHiddenAppsInSearch = preference(
        key = booleanPreferencesKey(name = "show_hidden_apps_in_search"),
        defaultValue = false,
    )

    val enableSmartHide = preference(
        key = booleanPreferencesKey(name = "enable_smart_hide"),
        defaultValue = false,
    )

    val hiddenAppsInSearch = preference(
        key = stringPreferencesKey(name = "hidden_apps_in_search"),
        defaultValue = HiddenAppsInSearch.NEVER,
        onSet = { reloadHelper.recreate() },
    )

    val searchAlgorithm = preference(
        key = stringPreferencesKey(name = "search_algorithm"),
        defaultValue = LawnchairSearchAlgorithm.LOCAL_SEARCH,
        onSet = { reloadHelper.recreate() },
    )

    val showSuggestedAppsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_suggested_apps_at_drawer_top"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_suggested_apps_at_drawer_top),
        onSet = { reloadHelper.recreate() },
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

    val workspaceTextColor = preference(
        key = stringPreferencesKey(name = "workspace_text_color"),
        defaultValue = ColorMode.AUTO,
        parse = { ColorMode.fromString(it) ?: ColorMode.AUTO },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val homeIconSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPreviewBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_preview_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_preview_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsOnHomeScreen = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsOnHomeScreenFolder = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen_folder"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
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

    val homeIconLabelFolderSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_folder_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_folder_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val pageIndicatorHeightFactor = preference(
        key = floatPreferencesKey(name = "page_indicator_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.page_indicator_height_factor),
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

    val drawerLeftRightMarginFactor = preference(
        key = floatPreferencesKey(name = "drawer_left_right_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_left_right_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hotseatBottomFactor = preference(
        key = floatPreferencesKey(name = "hotseat_bottom_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_hotseat_bottom_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFuzzySearch = preference(
        key = booleanPreferencesKey(name = "enable_fuzzy_search"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_fuzzy_search),
    )

    val closingAppOverlay = preference(
        key = stringPreferencesKey(name = "closing_app_overlay"),
        defaultValue = FullScreenOverlayMode.fromValue(context.resources.getString(R.string.config_default_overlay)),
        parse = { FullScreenOverlayMode.fromValue(it) },
        save = { it.value },
        onSet = { reloadHelper.reloadGrid() },
    )

    val matchHotseatQsbStyle = preference(
        key = booleanPreferencesKey(name = "use_drawer_search_icon"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProvider = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider"),
        defaultValue = WebSearchProvider.fromString(
            getRemoteDefault("web_suggestion_provider")
                ?: context.resources.getString(R.string.config_default_web_suggestion_provider),
        ),
        parse = { WebSearchProvider.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProviderUrl = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_url"),
        defaultValue = "",
    )

    val webSuggestionProviderSuggestionsUrl = preference(
        key = stringPreferencesKey(name = "web_suggestions_provider_suggestions_url"),
        defaultValue = "",
    )

    val webSuggestionProviderName = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_name"),
        defaultValue = context.resources.getString(R.string.custom),
    )

    val maxAppSearchResultCount = preference(
        key = intPreferencesKey(name = "max_search_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_search_max_result_count),
    )

    val maxWebSuggestionResultCount = preference(
        key = intPreferencesKey(name = "max_suggestion_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_suggestion_max_result_count),
    )

    val maxFileResultCount = preference(
        key = intPreferencesKey(name = "max_files_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_files_max_result_count),
    )

    val maxPeopleResultCount = preference(
        key = intPreferencesKey(name = "max_people_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_people_max_result_count),
    )

    val maxWebSuggestionDelay = preference(
        key = intPreferencesKey(name = "max_web_suggestion_delay"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_max_web_suggestion_delay),
    )

    val maxSettingsEntryResultCount = preference(
        key = intPreferencesKey(name = "max_settings_entry_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_settings_entry_max_result_count),
    )

    val maxRecentResultCount = preference(
        key = intPreferencesKey(name = "max_recent_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_recent_max_result_count),
    )

    val enableSmartspace = preference(
        key = booleanPreferencesKey(name = "enable_smartspace"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_smartspace),
        onSet = { reloadHelper.restart() },
    )

    val enableDotPagination = preference(
        key = booleanPreferencesKey(name = "enable_dot_pagination"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_dot_pagination),
        onSet = { reloadHelper.recreate() },
    )

    val enableMaterialUPopUp = preference(
        key = booleanPreferencesKey(name = "enable_material_u_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_material_u_popup),
        onSet = { reloadHelper.recreate() },
    )

    val twoLineAllApps = preference(
        key = booleanPreferencesKey(name = "two_line_all_apps"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_two_line_allapps),
        onSet = { reloadHelper.recreate() },
    )

    val enableFeed = preference(
        key = booleanPreferencesKey(name = "enable_feed"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_feed),
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
        defaultSelector = { numFolderColumns[INDEX_DEFAULT] },
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
        },
    )

    val smartspaceMode = preference(
        key = stringPreferencesKey("smartspace_mode"),
        defaultValue = SmartspaceMode.fromString(context.getString(R.string.config_default_smartspace_mode)),
        parse = { SmartspaceMode.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val smartspaceAagWidget = preference(
        key = booleanPreferencesKey("enable_smartspace_aag_widget"),
        defaultValue = true,
    )

    val smartspaceBatteryStatus = preference(
        key = booleanPreferencesKey("enable_smartspace_battery_status"),
        defaultValue = true,
    )

    val smartspaceNowPlaying = preference(
        key = booleanPreferencesKey("enable_smartspace_now_playing"),
        defaultValue = true,
    )

    val smartspaceShowDate = preference(
        key = booleanPreferencesKey("smartspace_show_date"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_date),
    )

    val smartspaceShowTime = preference(
        key = booleanPreferencesKey("smartspace_show_time"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_time),
    )

    val smartspaceTimeFormat = preference(
        key = stringPreferencesKey("smartspace_time_format"),
        defaultValue = SmartspaceTimeFormat.fromString(context.getString(R.string.config_default_smartspace_time_format)),
        parse = { SmartspaceTimeFormat.fromString(it) },
        save = { it.toString() },
    )

    val smartspaceCalendar = preference(
        key = stringPreferencesKey(name = "smartspace_calendar"),
        defaultValue = SmartspaceCalendar.fromString(context.getString(R.string.config_default_smart_space_calendar)),
        parse = { SmartspaceCalendar.fromString(it) },
        save = { it.toString() },
    )

    val smartspacerMaxCount = preference(
        key = intPreferencesKey(name = "smartspace_max_count"),
        defaultValue = 5,
        onSet = { reloadHelper.recreate() },
    )

    val wallpaperDepthEffect = preference(
        key = booleanPreferencesKey(name = "enable_wallpaper_depth_effect"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val deckLayout = preference(
        key = booleanPreferencesKey(name = "enable_lawn_deck"),
        defaultValue = false,
        onSet = { reloadHelper.reloadIcons() },
    )

    val showDeckLayout = preference(
        key = booleanPreferencesKey(name = "show_deck_layout"),
        defaultValue = false,
    )

    val enableLabelInDock = preference(
        key = booleanPreferencesKey(name = "enable_label_dock"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    val iconSwipeGestures = preference(
        key = booleanPreferencesKey(name = "icon_swipe_gestures"),
        defaultValue = false,
    )

    val doubleTapGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("double_tap_gesture_handler"),
        defaultValue = GestureHandlerConfig.Sleep,
    )

    val swipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenAppDrawer,
    )

    val swipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenNotifications,
    )

    val homePressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("home_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val backPressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("back_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    private inline fun <reified T> serializablePreference(
        key: Preferences.Key<String>,
        defaultValue: T,
    ) = preference(
        key = key,
        defaultValue = defaultValue,
        parse = { value ->
            runCatching { kotlinxJson.decodeFromString<T>(value) }.getOrDefault(defaultValue)
        },
        save = kotlinxJson::encodeToString,
    )

    init {
        initializeIconShape(iconShape.firstBlocking())
        iconShape.get()
            .drop(1)
            .distinctUntilChanged()
            .onEach { shape ->
                initializeIconShape(shape)
                L3IconShape.INSTANCE.get(context)
                LauncherAppState.getInstance(context).reloadIcons()
            }
            .launchIn(scope)
    }

    suspend fun setGestureForApp(
        key: ComponentKey,
        gestureType: GestureType,
        gesture: GestureHandlerConfig,
    ) {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        preferencesDataStore.edit { prefs ->
            prefs[key] = kotlinxJson.encodeToString(gesture)
        }
    }

    fun getGestureForApp(key: ComponentKey, gestureType: GestureType): Flow<GestureHandlerConfig> {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        return preferencesDataStore.data.map { prefs ->
            prefs[key]?.let {
                runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(it) }
                    .getOrDefault(GestureHandlerConfig.NoOp)
            } ?: GestureHandlerConfig.NoOp
        }
    }

    private fun initializeIconShape(shape: IconShape) {
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskId = shape.getHashString()
        CustomAdaptiveIconDrawable.sMask = shape.getMaskPath()
    }

    override fun close() {
    }

    private fun getRemoteDefault(key: String): String? = liveInformationManager.liveInformation
        .firstBlocking()
        .features[key]
        .also { value ->
            if (value == null) {
                Log.d(TAG, "getRemoteDefault: $key -> no remote default")
            } else {
                Log.d(TAG, "getRemoteDefault: $key -> $value")
            }
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

        private const val TAG = "PreferenceManager2"
    }
}

@Composable
fun preferenceManager2() = PreferenceManager2.getInstance(LocalContext.current)
