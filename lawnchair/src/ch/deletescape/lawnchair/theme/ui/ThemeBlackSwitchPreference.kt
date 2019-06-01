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

package ch.deletescape.lawnchair.theme.ui

import android.content.Context
import android.support.annotation.Keep
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.util.hasFlag

@Keep
class ThemeBlackSwitchPreference(context: Context, attrs: AttributeSet) : ThemeFlagSwitchPreference(context, attrs) {

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        super.onValueChanged(key, prefs, force)

        isEnabled = prefs.launcherTheme.hasFlag(ThemeManager.THEME_DARK_MASK)
    }
}
