package ch.deletescape.lawnchair.graphics

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.text.TextUtils
import android.util.Log
import ch.deletescape.lawnchair.LauncherAppState
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.preferences.IPreferenceProvider
import ch.deletescape.lawnchair.preferences.blockingEdit
import java.lang.reflect.Field

@TargetApi(Build.VERSION_CODES.O)
class IconShapeOverride {

    class PreferenceChangeHandler constructor(val context: Context) : Preference.OnPreferenceChangeListener {

        override fun onPreferenceChange(preference: Preference, obj: Any): Boolean {
            val str = obj as String
            if (getAppliedValue(context).savedPref != str) {
                prefs(context).blockingEdit { overrideIconShape = str }
                LauncherAppState.getInstance().iconCache.clear()
                Utilities.restartLauncher(context)
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

        const val planeMask = "M21,16V14L13,9V3.5A1.5,1.5 0 0,0 11.5,2A1.5,1.5 0 0,0 10,3.5V9L2,14V16L10,13.5V19L8,20.5V22L11.5,21L15,22V20.5L13,19V13.5L21,16Z"
        const val defaultMask = "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z"

        fun isSupported(context: Context): Boolean {
            if (Utilities.ATLEAST_NOUGAT && prefs(context).backportAdaptiveIcons) {
                return true
            }
            if (!Utilities.ATLEAST_OREO) {
                return false
            }
            try {
                return (systemResField.get(null) === Resources.getSystem() && configResId != 0)
            } catch (e: Exception) {
                return false
            }

        }

        fun apply(context: Context) {
            if (Utilities.ATLEAST_OREO) {
                val appliedValue = getAppliedValue(context)
                if (!TextUtils.isEmpty(appliedValue.maskPath) && isSupported(context)) {
                    try {
                        systemResField.set(null, ResourcesOverride(Resources.getSystem(), configResId, appliedValue.maskPath))
                    } catch (e: Throwable) {
                        Log.e("IconShapeOverride", "Unable to override icon shape", e)
                        prefs(context).removeOverrideIconShape()
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

        fun getAppliedValue(context: Context): ShapeInfo {
            val prefs = prefs(context)
            if (!Utilities.ATLEAST_NOUGAT) {
                val mask = if (prefs.usePixelIcons) defaultMask else ""
                return ShapeInfo(mask, mask, 100, prefs.usePixelIcons)
            }
            val enablePlanes = prefs.enablePlanes
            var iconShape = if (enablePlanes) planeMask else prefs.overrideIconShape
            val savedPref = iconShape
            val useRoundIcon = iconShape != "none"
            return ShapeInfo(if (iconShape == "none") "" else iconShape, savedPref, if (enablePlanes) 24 else 100, useRoundIcon)
        }

        private fun prefs(context: Context): IPreferenceProvider {
            return Utilities.getPrefs(context)
        }

        fun handlePreferenceUi(listPreference: ListPreference) {
            val context = listPreference.context
            listPreference.value = getAppliedValue(context).savedPref
            listPreference.onPreferenceChangeListener = PreferenceChangeHandler(context)
        }
    }

    data class ShapeInfo(
            val maskPath: String,
            val savedPref: String,
            val size: Int,
            val useRoundIcon: Boolean,
            val xmlAttrName: String = if (useRoundIcon) "roundIcon" else "icon")
}
