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

package ch.deletescape.lawnchair.groups

import ch.deletescape.lawnchair.LawnchairPreferences

class AppGroupsManager(val prefs: LawnchairPreferences) {

    var categorizationEnabled by prefs.BooleanPref("pref_appsCategorizationEnabled", true, ::onPrefsChanged)
    var categorizationType by prefs.EnumPref("pref_appsCategorizationType", CategorizationType.Tabs, ::onPrefsChanged)

    val drawerTabs by lazy { DrawerTabs(this) }
    val drawerFolders by lazy { DrawerFolders(this) }

    private fun onPrefsChanged() {
        prefs.getOnChangeCallback()?.let {
            drawerTabs.checkIsEnabled(it)
        }
    }

    fun getEnabledType(): CategorizationType? {
        return CategorizationType.values().firstOrNull { getModel(it).isEnabled }
    }

    private fun getModel(type: CategorizationType): AppGroups<*> {
        return when (type) {
            CategorizationType.Tabs -> drawerTabs
            CategorizationType.Folders -> drawerFolders
        }
    }

    enum class CategorizationType(val prefsKey: String) {

        Tabs("pref_drawerTabs"),
        Folders("pref_drawerFolders")
    }
}
