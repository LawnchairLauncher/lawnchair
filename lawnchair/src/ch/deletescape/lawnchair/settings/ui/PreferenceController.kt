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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.support.v7.preference.Preference

open class PreferenceController(val context: Context) {

    open val title: String? = null
    open val summary: String? = null
    open val onClick: Preference.OnPreferenceClickListener? = null
    open val onChange: Preference.OnPreferenceChangeListener? = null
    open val isVisible = true

    open fun onPreferenceAdded(preference: Preference): Boolean {
        if (!isVisible) {
            preference.parent?.removePreference(preference)
            return false
        }
        title?.let { preference.title = it }
        summary?.let { preference.summary = it }
        onClick?.let { preference.onPreferenceClickListener = it }
        onChange?.let { preference.onPreferenceChangeListener = it }
        return true
    }

    companion object {

        fun create(context: Context, controllerClass: String?): PreferenceController? {
            if (controllerClass?.startsWith(".") == true) {
                return create(context, "ch.deletescape.lawnchair.settings.ui.controllers$controllerClass")
            }
            return try {
                Class.forName(controllerClass!!).getConstructor(Context::class.java)
                        .newInstance(context) as PreferenceController
            } catch (t: Throwable) {
                null
            }
        }
    }
}
