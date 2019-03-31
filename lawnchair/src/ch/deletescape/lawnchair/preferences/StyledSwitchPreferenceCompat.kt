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
import android.content.res.ColorStateList
import android.support.v14.preference.SwitchPreference
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.preference.AndroidResources
import android.support.v7.preference.PreferenceViewHolder
import android.support.v7.widget.SwitchCompat
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.settings.ui.search.SearchIndex
import com.android.launcher3.util.Themes

open class StyledSwitchPreferenceCompat @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        SwitchPreference(context, attrs), ColorEngine.OnColorChangeListener,
        ControlledPreference by ControlledPreference.Delegate(context, attrs), SearchIndex.Slice {

    protected var checkableView: View? = null
        private set

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        checkableView = holder?.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET)
        ColorEngine.getInstance(context).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolver: String, color: Int, foregroundColor: Int) {
        if (resolver == ColorEngine.Resolvers.ACCENT && checkableView is Switch) {
            (checkableView as Switch).applyColor(color)
        }
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun getSlice(context: Context, key: String): View {
        val color = ColorEngine.getInstance(context).accent
        val prefs = context.lawnchairPrefs
        var pref by prefs.BooleanPref(key)
        return Switch(context).apply {
            applyColor(color)
            prefs.addOnPreferenceChangeListener(key, object : LawnchairPreferences.OnPreferenceChangeListener {
                override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
                    isChecked = pref
                }
            })
            setOnCheckedChangeListener { _, isChecked ->
                pref = isChecked
            }
        }
    }
}
