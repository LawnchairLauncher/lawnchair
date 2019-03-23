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

package ch.deletescape.lawnchair.sesame.preferences

import android.content.Context
import android.os.Parcelable
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.Toast
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.globalsearch.providers.SesameSearchProvider
import ch.deletescape.lawnchair.preferences.ResumablePreference
import ch.deletescape.lawnchair.preferences.StyledSwitchPreferenceCompat
import ch.deletescape.lawnchair.sesame.Sesame
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper
import ninja.sesame.lib.bridge.v1.SesameFrontend

class SesameSearchUpsellPreference(context: Context, attrs: AttributeSet?): StyledSwitchPreferenceCompat(context, attrs) {
    private val prefs = context.lawnchairPrefs

    init {
        isPersistent = false // !important
        super.setChecked(prefs.searchProvider == Sesame.SEARCH_PROVIDER_CLASS)
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        prefs.searchProvider = if (checked) {
            Sesame.SEARCH_PROVIDER_CLASS
        } else {
            context.resources.getString(R.string.config_default_search_provider)
        }
    }
}