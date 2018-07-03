package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.smartspace.OWMWeatherDataProvider
import com.android.launcher3.Utilities

class OWMEditTextPreference(context: Context, attrs: AttributeSet?) : EditTextPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener  {
    private val isApi = key == "pref_weatherApiKey"
    private val prefs = Utilities.getLawnchairPrefs(context)

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
        prefs.addOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun persistString(value: String?): Boolean {
        if (!TextUtils.isEmpty(value))
            return super.persistString(value).apply { updateSummary() }
        return false
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key == "pref_smartspace_widget_provider") {
            isVisible = prefs.weatherProvider == OWMWeatherDataProvider::class.java.name
        }
    }

    private fun updateSummary() {
        if (isApi){
            summary = prefs.weatherApiKey.replace("[\\d\\w]".toRegex(), "*")
        } else {
            summary = prefs.weatherCity
        }
    }

}