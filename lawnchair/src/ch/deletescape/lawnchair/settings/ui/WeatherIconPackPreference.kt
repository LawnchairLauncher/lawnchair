/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.Preference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.smartspace.weather.icons.WeatherIconManager
import com.android.launcher3.R

class WeatherIconPackPreference(context: Context, attrs: AttributeSet? = null) : DialogPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {
    private val prefs = context.lawnchairPrefs
    private val manager = WeatherIconManager.getInstance(context)
    private val current get() = manager.getPack()

    init {
        layoutResource = R.layout.pref_with_preview_icon
        dialogLayoutResource = R.layout.pref_dialog_icon_pack
    }

    override fun onAttached() {
        super.onAttached()

        prefs.addOnPreferenceChangeListener(KEY, this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        icon = current.icon
        summary = current.name
    }

    override fun onDetached() {
        super.onDetached()

        prefs.removeOnPreferenceChangeListener(KEY, this)
    }

    companion object {
        const val KEY = "pref_weatherIcons"
    }
}