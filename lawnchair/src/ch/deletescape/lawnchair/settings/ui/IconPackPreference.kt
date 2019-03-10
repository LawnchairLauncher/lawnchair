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
import android.content.Intent
import android.graphics.drawable.Drawable
import android.support.v7.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.iconpack.DefaultPack
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.preferences.IconPackFragment
import ch.deletescape.lawnchair.settings.ui.search.SearchIndex
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs),
        LawnchairPreferences.OnPreferenceChangeListener, SearchIndex.Slice {
    private val packList by lazy { IconPackManager.getInstance(context).packList }

    init {
        layoutResource = R.layout.pref_with_preview_icon
        fragment = IconPackFragment::class.java.name
    }

    override fun onAttached() {
        super.onAttached()

        context.lawnchairPrefs.addOnPreferenceChangeListener(key, this)
    }

    override fun onDetached() {
        super.onDetached()

       context.lawnchairPrefs.removeOnPreferenceChangeListener(key, this)
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

    override fun getSlice(context: Context, key: String): View {
        return (View.inflate(context, R.layout.preview_icon, null) as ImageView).apply {
            IconPackManager.getInstance(context).addListener {
                setImageDrawable(packList.currentPack().displayIcon)
            }
            setOnClickListener {
                context.startActivity(Intent()
                        .setClass(context, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_FRAGMENT, fragment)
                )
            }
        }
    }
}
