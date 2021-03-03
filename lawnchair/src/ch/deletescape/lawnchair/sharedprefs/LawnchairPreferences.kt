package ch.deletescape.lawnchair.sharedprefs

import android.content.Context
import android.content.SharedPreferences
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

        fun getInstance(context: Context?): SharedPreferences? {
            if (context == null) return null
            return if (INSTANCE == null) {
                Utilities.getPrefs(context)
            } else {
                INSTANCE
            }
        }
    }
}