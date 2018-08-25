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
import android.support.v14.preference.SwitchPreference
import android.util.AttributeSet
import com.android.launcher3.Utilities
import kotlin.reflect.KMutableProperty1

class DockSwitchPreference(context: Context, attrs: AttributeSet?) : StyledSwitchPreferenceCompat(context, attrs) {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val currentStyle get() = prefs.dockStyles.currentStyle

    @Suppress("UNCHECKED_CAST")
    private val property = DockStyle.properties[key] as KMutableProperty1<DockStyle, Boolean>

    private val onChangeListener = { isChecked = property.get(currentStyle) }

    init {
        isChecked = property.get(currentStyle)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        isChecked = property.get(currentStyle)
    }

    override fun onAttached() {
        super.onAttached()
        prefs.dockStyles.addListener(onChangeListener)
    }

    override fun onDetached() {
        super.onDetached()
        prefs.dockStyles.removeListener(onChangeListener)
    }

    override fun getPersistedBoolean(defaultReturnValue: Boolean): Boolean {
        return property.get(currentStyle)
    }

    override fun persistBoolean(value: Boolean): Boolean {
        property.set(currentStyle, value)
        return true
    }
}
