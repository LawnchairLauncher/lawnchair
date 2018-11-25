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

package ch.deletescape.lawnchair.settings.ui.controllers

import android.content.Context
import android.support.annotation.Keep
import android.support.v7.preference.Preference
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.settings.ui.PreferenceController

@Keep
class IconPackMaskingController(context: Context) : PreferenceController(context) {

    override val isVisible = IconPackManager.getInstance(context).maskSupported()

    override fun onPreferenceAdded(preference: Preference): Boolean {
        // Don't remove from ui because this is already handled by SettingsActivity
        return true
    }
}
