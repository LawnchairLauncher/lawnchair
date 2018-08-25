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
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {
    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val packs by lazy { IconPackManager.getInstance(context).getPackProviderInfos() }
    private val default by lazy { IconPackManager.IconPackInfo("", context.getIcon(), context.getString(R.string.iconpack_none)) }

    init {
        IconPackManager.getInstance(context)
        layoutResource = R.layout.pref_with_preview_icon
        Utilities.getLawnchairPrefs(context).addOnPreferenceChangeListener("pref_icon_pack", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        updatePreview()
    }

    private fun updatePreview() {
        val pack = if (prefs.iconPack == "") default else packs[prefs.iconPack]
        summary = pack?.label
        icon = pack?.icon
    }

    override fun getDialogLayoutResource() = R.layout.pref_dialog_icon_pack
}