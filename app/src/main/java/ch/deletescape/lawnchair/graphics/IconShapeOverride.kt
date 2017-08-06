package ch.deletescape.lawnchair.graphics

import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.provider.Settings.Global
import android.text.TextUtils
import android.util.Log
import ch.deletescape.lawnchair.*
import java.lang.reflect.Field

@TargetApi(Build.VERSION_CODES.O)
class IconShapeOverride {

    class PreferenceChangeHandler constructor(val context: Context) : OnPreferenceChangeListener {

        override fun onPreferenceChange(preference: Preference, obj: Any): Boolean {
            val str = obj as String
            if (getAppliedValue(context) != str) {
                prefs(context).edit()
                        .putString("pref_override_icon_shape", str)
                        .commit()
                LauncherAppState.getInstance().iconCache.clear()
                Process.killProcess(Process.myPid())
            }
            return true
        }
    }

    class ResourcesOverride(resources: Resources, val overrideId: Int, val overrideValue: String)
        : Resources(resources.assets, resources.displayMetrics, resources.configuration) {

        override fun getString(i: Int): String {
            if (i == overrideId) {
                return overrideValue
            }
            return super.getString(i)
        }
    }

    companion object {

        fun isSupported(context: Context): Boolean {
            if (!Utilities.isAtLeastO() || Global.getInt(context.contentResolver, "development_settings_enabled", 0) != 1) {
                return false
            }
            try {
                return (systemResField.get(null) === Resources.getSystem() && configResId != 0)
            } catch (e: Exception) {
                return false
            }

        }

        fun apply(context: Context) {
            if (Utilities.isAtLeastO()) {
                val appliedValue = getAppliedValue(context)
                if (!TextUtils.isEmpty(appliedValue) && isSupported(context)) {
                    try {
                        systemResField.set(null, ResourcesOverride(Resources.getSystem(), configResId, appliedValue))
                    } catch (e: Throwable) {
                        Log.e("IconShapeOverride", "Unable to override icon shape", e)
                        prefs(context).edit().remove("pref_override_icon_shape").apply()
                    }

                }
            }
        }

        private val systemResField: Field
            get() {
                val declaredField = Resources::class.java.getDeclaredField("mSystem")
                declaredField.isAccessible = true
                return declaredField
            }

        private val configResId: Int
            get() = Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

        private fun getAppliedValue(context: Context): String {
            return prefs(context).getString("pref_override_icon_shape", "")
        }

        private fun prefs(context: Context): SharedPreferences {
            return Utilities.getPrefs(context)
        }

        fun handlePreferenceUi(listPreference: ListPreference) {
            val context = listPreference.context
            listPreference.value = getAppliedValue(context)
            listPreference.onPreferenceChangeListener = PreferenceChangeHandler(context)
        }
    }
}