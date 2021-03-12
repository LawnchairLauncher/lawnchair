package ch.deletescape.lawnchair.sharedprefs

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.Utilities

class PrefManager(context: Context) {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    private val lp = LawnchairPreferences
    var iconPackPackage by StringPrefDelegate(lp.ICON_PACK_PACKAGE, "")
    var allowRotation by BoolPrefDelegate("pref_allowRotation", true)
    var wrapAdaptiveIcons by BoolPrefDelegate(lp.WRAP_ADAPTIVE_ICONS, false)
    var addIconToHome by BoolPrefDelegate("pref_add_icon_to_home", true)
    var hotseatColumns by FloatPrefDelegate(lp.HOTSEAT_COLUMNS, 5F)
    var workspaceColumns by FloatPrefDelegate(lp.WORKSPACE_COLUMNS, 5F)
    var workspaceRows by FloatPrefDelegate(lp.WORKSPACE_ROWS, 7F)
    var folderColumns by FloatPrefDelegate(lp.FOLDER_COLUMNS, 3F)
    var folderRows by FloatPrefDelegate(lp.FOLDER_ROWS, 4F)
    var iconSizeFactor by FloatPrefDelegate(lp.ICON_SIZE_FACTOR, 1F)
    var textSizeFactor by FloatPrefDelegate(lp.TEXT_SIZE_FACTOR, 1F)
    var allAppsIconSizeFactor by FloatPrefDelegate(lp.ALL_APPS_ICON_SIZE_FACTOR, 1F)
    var allAppsTextSizeFactor by FloatPrefDelegate(lp.ALL_APPS_TEXT_SIZE_FACTOR, 1F)
    var allAppsColumns by FloatPrefDelegate(lp.ALL_APPS_COLUMNS, 5F)
    var allowEmptyPages by BoolPrefDelegate(lp.ALLOW_EMPTY_PAGES, false)
    var makeColoredBackgrounds by BoolPrefDelegate(lp.MAKE_COLORED_BACKGROUNDS, false)
}