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

package ch.deletescape.lawnchair.font.settingsui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.TextView
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.font.FontCache
import ch.deletescape.lawnchair.settings.ui.BasePreference
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.settings.ui.SettingsActivity

open class FontPreference(context: Context, attrs: AttributeSet) : BasePreference(context, attrs),
        FontCache.Font.LoadCallback {

    private val pref = CustomFontManager.getInstance(context).fontPrefs.getValue(key)
    private var typeface: Typeface? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    fun reloadFont() {
        val font = pref.actualFont
        font.load(this)
        summary = font.fullDisplayName
    }

    override fun onFontLoaded(typeface: Typeface?) {
        this.typeface = typeface
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val summary = holder.findViewById(android.R.id.summary) as TextView
        typeface?.let { summary.typeface = it }
    }

    override fun onClick() {
        context.startActivity(Intent(context, FontSelectionActivity::class.java)
                .putExtra(FontSelectionActivity.EXTRA_KEY, key))
    }

    override fun onAttached() {
        super.onAttached()

        pref.preferenceUi = this
        reloadFont()
    }

    override fun onDetached() {
        super.onDetached()

        pref.preferenceUi = null
    }
}
