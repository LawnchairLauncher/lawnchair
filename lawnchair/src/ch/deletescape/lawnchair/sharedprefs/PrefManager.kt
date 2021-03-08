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
}