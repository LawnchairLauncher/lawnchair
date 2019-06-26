/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.text.TextUtils
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.smartspace.AccuWeatherDataProvider
import ch.deletescape.lawnchair.smartspace.OWMWeatherDataProvider
import com.android.launcher3.Utilities

class OWMEditTextPreference(context: Context, attrs: AttributeSet?) : EditTextPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener  {
    private val isApi = key == "pref_weatherApiKey"
    private val prefs = Utilities.getLawnchairPrefs(context)

    override fun onAttached() {
        super.onAttached()

        updateSummary()
        prefs.addOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun onDetached() {
        super.onDetached()

        prefs.removeOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun persistString(value: String?): Boolean {
        if (!TextUtils.isEmpty(value))
            return super.persistString(value).apply { updateSummary() }
        return false
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key == "pref_smartspace_widget_provider") {
            isVisible = prefs.weatherProvider == OWMWeatherDataProvider::class.java.name
                    || !isApi && prefs.weatherProvider == AccuWeatherDataProvider::class.java.name
        }
    }

    private fun updateSummary() {
        summary = if (isApi) {
            prefs.weatherApiKey.replace("[\\d\\w]".toRegex(), "*")
        } else {
            prefs.weatherCity
        }
    }

}
