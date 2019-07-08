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
import android.support.v7.preference.ListPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.util.addFlag
import ch.deletescape.lawnchair.util.buildEntries
import ch.deletescape.lawnchair.util.removeFlag
import com.android.launcher3.R

@Keep
class ThemeDarkModeListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs),
        LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = context.lawnchairPrefs

    init {
        buildEntries {
            addEntry(R.string.theme_dark_theme_mode_follow_wallpaper, ThemeManager.THEME_FOLLOW_WALLPAPER)
            addEntry(R.string.theme_dark_theme_mode_follow_system, ThemeManager.THEME_FOLLOW_NIGHT_MODE)
            addEntry(R.string.theme_dark_theme_mode_follow_daylight, ThemeManager.THEME_FOLLOW_DAYLIGHT)
            addEntry(R.string.theme_dark_theme_mode_on, ThemeManager.THEME_DARK)
            addEntry(R.string.theme_dark_theme_mode_off, 0)
        }
    }

    override fun onAttached() {
        super.onAttached()

        prefs.addOnPreferenceChangeListener("pref_launcherTheme", this)
    }

    override fun onDetached() {
        super.onDetached()

        prefs.removeOnPreferenceChangeListener("pref_launcherTheme", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        value = "${prefs.launcherTheme and ThemeManager.THEME_DARK_MASK}"
    }

    override fun persistString(value: String): Boolean {
        val newFlag = prefs.launcherTheme
                .removeFlag(ThemeManager.THEME_DARK_MASK)
                .addFlag(value.toInt())
        if (prefs.launcherTheme != newFlag) {
            prefs.launcherTheme = newFlag
        }
        return true
    }

}
