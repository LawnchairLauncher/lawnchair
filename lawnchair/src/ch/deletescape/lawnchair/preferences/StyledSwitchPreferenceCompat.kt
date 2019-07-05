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
import android.support.v7.preference.AndroidResources
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.getColorEngineAccent
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.settings.ui.search.SearchIndex

open class StyledSwitchPreferenceCompat(context: Context, attrs: AttributeSet? = null) :
        SwitchPreference(context, attrs), ColorEngine.OnColorChangeListener,
        ControlledPreference by ControlledPreference.Delegate(context, attrs) {

    protected var checkableView: View? = null
        private set

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        checkableView = holder?.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET)
        ColorEngine.getInstance(context).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == ColorEngine.Resolvers.ACCENT && checkableView is Switch) {
            (checkableView as Switch).applyColor(resolveInfo.color)
        }
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    open class SwitchSlice(context: Context, attrs: AttributeSet) : SearchIndex.Slice(context, attrs) {

        private val defaultValue: Boolean

        init {
            val ta = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.defaultValue))
            defaultValue = ta.getBoolean(0, false)
            ta.recycle()
        }

        override fun createSliceView(): View {
            return SwitchSliceView(context, key, defaultValue)
        }
    }

    class SwitchSliceView(
            context: Context,
            private val key: String,
            private val defaultValue: Boolean)
        : Switch(context), LawnchairPreferences.OnPreferenceChangeListener {

        init {
            applyColor(context.getColorEngineAccent())
            setOnCheckedChangeListener { _, isChecked ->
                context.lawnchairPrefs.sharedPrefs.edit().putBoolean(key, isChecked).apply()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            context.lawnchairPrefs.addOnPreferenceChangeListener(key, this)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            context.lawnchairPrefs.removeOnPreferenceChangeListener(key, this)
        }

        override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
            isChecked = prefs.sharedPrefs.getBoolean(key, defaultValue)
        }
    }

    companion object {

        @Keep
        @JvmStatic
        val sliceProvider = SearchIndex.SliceProvider.fromLambda(::SwitchSlice)
    }
}
