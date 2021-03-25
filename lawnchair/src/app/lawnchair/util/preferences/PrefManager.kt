package app.lawnchair.util.preferences

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities

class PrefManager(context: Context) {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    private val lp = LawnchairPreferences
    private val idp = LauncherAppState.getIDP(context)
    private val launcher = LauncherAppState.getInstance(context).launcher
    var iconPackPackage by StringPrefDelegate(lp.ICON_PACK_PACKAGE, "")
    var allowRotation by BoolPrefDelegate("pref_allowRotation", true)
    var wrapAdaptiveIcons by BoolPrefDelegate(lp.WRAP_ADAPTIVE_ICONS, false)
    var addIconToHome by BoolPrefDelegate("pref_add_icon_to_home", true)
    var hotseatColumns by FloatPrefDelegate(lp.HOTSEAT_COLUMNS, idp.numHotseatIcons.toFloat())
    var workspaceColumns by FloatPrefDelegate(lp.WORKSPACE_COLUMNS, idp.numColumns.toFloat())
    var workspaceRows by FloatPrefDelegate(lp.WORKSPACE_ROWS, idp.numRows.toFloat())
    var folderColumns by FloatPrefDelegate(lp.FOLDER_COLUMNS, idp.numFolderColumns.toFloat())
    var folderRows by FloatPrefDelegate(lp.FOLDER_ROWS, idp.numFolderRows.toFloat())
    var iconSizeFactor by FloatPrefDelegate(lp.ICON_SIZE_FACTOR, 1F)
    var textSizeFactor by FloatPrefDelegate(lp.TEXT_SIZE_FACTOR, 1F)
    var allAppsIconSizeFactor by FloatPrefDelegate(lp.ALL_APPS_ICON_SIZE_FACTOR, 1F)
    var allAppsTextSizeFactor by FloatPrefDelegate(lp.ALL_APPS_TEXT_SIZE_FACTOR, 1F)
    var allAppsColumns by FloatPrefDelegate(lp.ALL_APPS_COLUMNS, idp.numAllAppsColumns.toFloat())
    var allowEmptyPages by BoolPrefDelegate(lp.ALLOW_EMPTY_PAGES, false)
    var makeColoredBackgrounds by BoolPrefDelegate(lp.MAKE_COLORED_BACKGROUNDS, false)
    var drawerOpacity by FloatPrefDelegate(lp.DRAWER_OPACITY, launcher.scrimView.scrimAlpha.toFloat())
}