package app.lawnchair.util.preferences

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.Utilities

class PrefManager(context: Context) {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    private val lp = LawnchairPreferences
    var iconPackPackage by StringPrefDelegate(LawnchairPreferences.ICON_PACK_PACKAGE, "")
    var allowRotation by BoolPrefDelegate("pref_allowRotation", true)
    var wrapAdaptiveIcons by BoolPrefDelegate(LawnchairPreferences.WRAP_ADAPTIVE_ICONS, false)
    var addIconToHome by BoolPrefDelegate("pref_add_icon_to_home", true)
    var hotseatColumns by FloatPrefDelegate(LawnchairPreferences.HOTSEAT_COLUMNS, 5F)
    var workspaceColumns by FloatPrefDelegate(LawnchairPreferences.WORKSPACE_COLUMNS, 5F)
    var workspaceRows by FloatPrefDelegate(LawnchairPreferences.WORKSPACE_ROWS, 7F)
    var folderColumns by FloatPrefDelegate(LawnchairPreferences.FOLDER_COLUMNS, 3F)
    var folderRows by FloatPrefDelegate(LawnchairPreferences.FOLDER_ROWS, 4F)
    var iconSizeFactor by FloatPrefDelegate(LawnchairPreferences.ICON_SIZE_FACTOR, 1F)
    var textSizeFactor by FloatPrefDelegate(LawnchairPreferences.TEXT_SIZE_FACTOR, 1F)
    var allAppsIconSizeFactor by FloatPrefDelegate(LawnchairPreferences.ALL_APPS_ICON_SIZE_FACTOR, 1F)
    var allAppsTextSizeFactor by FloatPrefDelegate(LawnchairPreferences.ALL_APPS_TEXT_SIZE_FACTOR, 1F)
    var allAppsColumns by FloatPrefDelegate(LawnchairPreferences.ALL_APPS_COLUMNS, 5F)
    var allowEmptyPages by BoolPrefDelegate(LawnchairPreferences.ALLOW_EMPTY_PAGES, false)
    var makeColoredBackgrounds by BoolPrefDelegate(LawnchairPreferences.MAKE_COLORED_BACKGROUNDS, false)
}