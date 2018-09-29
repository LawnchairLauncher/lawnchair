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
import android.support.v7.preference.Preference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.iconpack.DefaultPack
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.preferences.IconPackFragment
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {
    private val packList by lazy { IconPackManager.getInstance(context).packList }

    init {
        layoutResource = R.layout.pref_with_preview_icon
        fragment = IconPackFragment::class.java.name
    }

    override fun onAttached() {
        super.onAttached()

        Utilities.getLawnchairPrefs(context).addOnPreferenceChangeListener("pref_icon_packs", this)
    }

    override fun onDetached() {
        super.onDetached()

        Utilities.getLawnchairPrefs(context).removeOnPreferenceChangeListener("pref_icon_packs", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        updatePreview()
    }

    private fun updatePreview() {
        summary = if (packList.currentPack() is DefaultPack) {
            packList.currentPack().displayName
        } else {
            packList.appliedPacks
                    .filter { it !is DefaultPack }
                    .joinToString(", ") { it.displayName }
        }
        icon = packList.currentPack().displayIcon
    }
}
