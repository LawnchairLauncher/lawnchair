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
import android.support.v7.preference.ListPreference
import android.util.AttributeSet
import com.android.launcher3.Utilities

class DockPresetPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private val prefs = Utilities.getLawnchairPrefs(context)

    private val property = prefs.dockStyles::dockPreset

    private val onChangeListener = {
        value = "${property.get()}"
        notifyDependencyChange(shouldDisableDependents())
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        value = "${property.get()}"
    }

    override fun shouldDisableDependents(): Boolean {
        return prefs.dockHide || super.shouldDisableDependents()
    }

    override fun onAttached() {
        super.onAttached()
        prefs.dockStyles.addListener(onChangeListener)
    }

    override fun onDetached() {
        super.onDetached()
        prefs.dockStyles.removeListener(onChangeListener)
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        return "${property.get()}"
    }

    override fun persistString(value: String?): Boolean {
        property.set(value?.toIntOrNull() ?: 1)
        return true
    }
}
