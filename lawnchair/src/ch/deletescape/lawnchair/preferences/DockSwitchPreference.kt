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
import android.support.annotation.Keep
import android.support.v14.preference.SwitchPreference
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import com.android.launcher3.Utilities
import kotlin.reflect.KMutableProperty1

class DockSwitchPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : StyledSwitchPreferenceCompat(context, attrs) {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val currentStyle get() = prefs.dockStyles.currentStyle
    private val inverted get() = key == "enableGradient"

    @Suppress("UNCHECKED_CAST")
    private val property get() = DockStyle.properties[key] as? KMutableProperty1<DockStyle, Boolean>

    private val onChangeListener = { isChecked = getPersistedBoolean(false) }

    init {
        isChecked = getPersistedBoolean(false)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        isChecked = getPersistedBoolean(false)
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
        if (inverted) return property?.get(currentStyle) != true
        return property?.get(currentStyle) == true
    }

    override fun persistBoolean(value: Boolean): Boolean {
        property?.set(currentStyle, if (inverted) !value else value)
        return property != null
    }

    override fun getSlice(context: Context, key: String): View {
        this.key = key
        return (super.getSlice(context, key) as Switch).apply {
            prefs.dockStyles.addListener {
                isChecked = getPersistedBoolean(false)
            }
            isChecked = getPersistedBoolean(false)
            setOnCheckedChangeListener { _, isChecked ->
                persistBoolean(isChecked)
            }
        }
    }
}
