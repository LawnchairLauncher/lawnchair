package ch.deletescape.lawnchair.sharedprefs

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.NonNull
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities

class LawnchairPreferences(val context: Context) {
    val listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            when (key) {
                ICON_PACK_PACKAGE -> {
                    LauncherAppState.getInstance(context).model.clearIconCache()
                    LauncherAppState.getInstance(context).model.forceReload()
                }
            }
        }

    companion object {

        private var INSTANCE: SharedPreferences? = null

        @kotlin.jvm.JvmField
        var ICON_PACK_PACKAGE: String = "pref_iconPackPackage"
        @kotlin.jvm.JvmField
        var HOTSEAT_COLUMNS: String = "pref_hotseatColumns"
        @kotlin.jvm.JvmField
        var WORKSPACE_COLUMNS: String = "pref_workspaceColumns"
        @kotlin.jvm.JvmField
        var WORKSPACE_ROWS: String = "pref_workspaceRows"
        @kotlin.jvm.JvmField
        var ALL_APPS_COLUMNS: String = "pref_allAppsColumns"
        @kotlin.jvm.JvmField
        var FOLDER_COLUMNS: String = "pref_folderColumns"
        @kotlin.jvm.JvmField
        var FOLDER_ROWS: String = "pref_folderRows"
        @kotlin.jvm.JvmField
        var ICON_SIZE_FACTOR: String = "pref_iconSizeFactor"
        @kotlin.jvm.JvmField
        var TEXT_SIZE_FACTOR: String = "pref_textSizeFactor"
        @kotlin.jvm.JvmField
        var ALL_APPS_ICON_SIZE_FACTOR: String = "pref_allAppsIconSizeFactor"
        @kotlin.jvm.JvmField
        var ALL_APPS_TEXT_SIZE_FACTOR: String = "pref_allAppsTextSizeFactor"

        fun getInstance(context: Context?): SharedPreferences? = when {
            context == null -> null
            INSTANCE == null -> Utilities.getPrefs(context)
            else -> INSTANCE
        }
    }
}