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

import android.content.Context
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.R

class DrawerTabs(prefs: LawnchairPreferences) : AppGroups<DrawerTabs.Tab>(prefs, "pref_drawerTabs") {

    override fun getDefaultGroups(): List<GroupCreator<Tab>> {
        return listOf(::createAllAppsTab, ::createPersonalTab, ::createWorkTab)
    }

    override fun getGroupCreator(type: Int): GroupCreator<Tab> {
        return when (type) {
            TYPE_CUSTOM, TYPE_UNDEFINED -> ::createCustomTab
            TYPE_PERSONAL -> ::createPersonalTab
            TYPE_WORK -> ::createWorkTab
            TYPE_ALL_APPS -> ::createAllAppsTab
            else -> ::createNull
        }
    }

    private fun createPersonalTab(context: Context) = PersonalTab(context)

    private fun createWorkTab(context: Context) = WorkTab(context)

    private fun createAllAppsTab(context: Context) = AllAppsTab(context)

    @Suppress("UNUSED_PARAMETER")
    private fun createCustomTab(context: Context) = CustomTab(context)

    abstract class Tab(context: Context, type: Int, titleRes: Int) : Group(type, context, titleRes) {

        val colorResolver = ColorRow(KEY_COLOR, AppGroupsUtils.getInstance(context).defaultColorResolver)

        init {
            addCustomization(colorResolver)
        }

        open fun getSummary(context: Context): String? = null
    }

    class CustomTab(context: Context) : Tab(context, TYPE_CUSTOM, R.string.default_tab_name) {

        val hideFromAllApps = SwitchRow(R.drawable.tab_hide_from_main, R.string.tab_hide_from_main,
                KEY_HIDE_FROM_ALL_APPS, true)
        val contents = AppsRow(KEY_ITEMS, mutableSetOf())

        init {
            addCustomization(hideFromAllApps)
            addCustomization(contents)

            customizations.setOrder(KEY_TITLE, KEY_HIDE_FROM_ALL_APPS, KEY_COLOR, KEY_ITEMS)
        }

        override fun getSummary(context: Context): String? {
            val size = contents.value().size
            return context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
        }
    }

    open class PredefinedTab(context: Context, type: Int, titleRes: Int) : Tab(context, type, titleRes) {

        init {
            customizations.setOrder(KEY_TITLE, KEY_COLOR)
        }
    }

    class AllAppsTab(context: Context) : PredefinedTab(context, TYPE_ALL_APPS, R.string.apps_label)

    class PersonalTab(context: Context) : PredefinedTab(context, TYPE_PERSONAL, R.string.all_apps_personal_tab)

    class WorkTab(context: Context) : PredefinedTab(context, TYPE_WORK, R.string.all_apps_work_tab)

    companion object {

        const val TYPE_PERSONAL = 0
        const val TYPE_WORK = 1
        const val TYPE_CUSTOM = 2
        const val TYPE_ALL_APPS = 3
    }
}
