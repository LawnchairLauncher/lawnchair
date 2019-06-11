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

package ch.deletescape.lawnchair.colors.resolvers

import android.support.annotation.Keep
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R
import com.android.launcher3.util.Themes

@Keep
class DrawerLabelAutoResolver(config: Config) : ColorEngine.ColorResolver(config), LawnchairPreferences.OnPreferenceChangeListener {

    override fun getDisplayName() = context.getString(R.string.theme_based)

    override fun startListening() {
        super.startListening()
        LawnchairPreferences.getInstanceNoCreate().addOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        LawnchairPreferences.getInstanceNoCreate().removeOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun resolveColor() = Themes.getAttrColor(launcherThemeContext, android.R.attr.textColorSecondary)
}

@Keep
class WorkspaceLabelAutoResolver(config: Config) : ColorEngine.ColorResolver(config), LawnchairPreferences.OnPreferenceChangeListener {

    override fun getDisplayName() = context.getString(R.string.theme_based)

    override fun startListening() {
        super.startListening()
        LawnchairPreferences.getInstanceNoCreate().addOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        LawnchairPreferences.getInstanceNoCreate().removeOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun resolveColor() = Themes.getAttrColor(launcherThemeContext, R.attr.workspaceTextColor)
}