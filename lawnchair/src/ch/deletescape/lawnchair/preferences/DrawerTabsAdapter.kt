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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.groups.AppGroups
import ch.deletescape.lawnchair.groups.DrawerTabs
import ch.deletescape.lawnchair.groups.ui.AppGroupsAdapter
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.R
import com.android.launcher3.compat.UserManagerCompat

class DrawerTabsAdapter(context: Context) : AppGroupsAdapter<DrawerTabsAdapter.TabHolder, DrawerTabs.Tab>(context) {

    override val groupsModel = manager.drawerTabs
    override val headerText = R.string.drawer_tabs

    private val hasWorkApps = context.lawnchairPrefs.separateWorkApps
            && UserManagerCompat.getInstance(context).userProfiles.size > 1

    override fun createGroup() = DrawerTabs.CustomTab(context)

    override fun createGroupHolder(parent: ViewGroup): TabHolder {
        return TabHolder(LayoutInflater.from(parent.context).inflate(R.layout.tab_item, parent, false))
    }

    override fun filterGroups(): Collection<DrawerTabs.Tab> {
        return if (hasWorkApps) {
            groupsModel.getGroups().filter { it !is DrawerTabs.AllAppsTab }
        } else {
            groupsModel.getGroups().filter { it !is DrawerTabs.PersonalTab && it !is DrawerTabs.WorkTab }
        }
    }

    inner class TabHolder(itemView: View) : GroupHolder(itemView) {

        override fun bind(info: AppGroups.Group) {
            super.bind(info)

            delete.isVisible = info is DrawerTabs.CustomTab
        }
    }
}
