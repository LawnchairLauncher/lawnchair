package app.lawnchair.util.preferences

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities

class PreferenceManager(context: Context) {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    private val lp = LawnchairPreferences
    private val idp = LauncherAppState.getIDP(context)
    var iconPackPackage by StringPreferenceDelegate(lp.ICON_PACK_PACKAGE, "")
    var allowRotation by BooleanPreferenceDelegate("pref_allowRotation", true)
    var wrapAdaptiveIcons by BooleanPreferenceDelegate(lp.WRAP_ADAPTIVE_ICONS, false)
    var addIconToHome by BooleanPreferenceDelegate("pref_add_icon_to_home", true)
    var hotseatColumns by FloatPreferenceDelegate(lp.HOTSEAT_COLUMNS, idp.numHotseatIcons.toFloat())
    var workspaceColumns by FloatPreferenceDelegate(lp.WORKSPACE_COLUMNS, idp.numColumns.toFloat())
    var workspaceRows by FloatPreferenceDelegate(lp.WORKSPACE_ROWS, idp.numRows.toFloat())
    var folderColumns by FloatPreferenceDelegate(lp.FOLDER_COLUMNS, idp.numFolderColumns.toFloat())
    var folderRows by FloatPreferenceDelegate(lp.FOLDER_ROWS, idp.numFolderRows.toFloat())
    var iconSizeFactor by FloatPreferenceDelegate(lp.ICON_SIZE_FACTOR, 1F)
    var textSizeFactor by FloatPreferenceDelegate(lp.TEXT_SIZE_FACTOR, 1F)
    var allAppsIconSizeFactor by FloatPreferenceDelegate(lp.ALL_APPS_ICON_SIZE_FACTOR, 1F)
    var allAppsTextSizeFactor by FloatPreferenceDelegate(lp.ALL_APPS_TEXT_SIZE_FACTOR, 1F)
    var allAppsColumns by FloatPreferenceDelegate(lp.ALL_APPS_COLUMNS, idp.numAllAppsColumns.toFloat())
    var allowEmptyPages by BooleanPreferenceDelegate(lp.ALLOW_EMPTY_PAGES, false)
    var drawerOpacity by FloatPreferenceDelegate(lp.DRAWER_OPACITY, 1F)
    var coloredBackgroundLightness by FloatPreferenceDelegate(lp.COLORED_BACKGROUND_LIGHTNESS, 0.9F)
}