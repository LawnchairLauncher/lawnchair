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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.groups.ui.AppGroupsAdapter
import com.android.launcher3.R

class DrawerFoldersAdapter(context: Context) : AppGroupsAdapter<DrawerFoldersAdapter.FolderHolder, DrawerFolders.Folder>(context) {

    override val groupsModel = manager.drawerFolders
    override val headerText = R.string.drawer_folders

    override fun createGroup(callback: (folder: DrawerFolders.Folder, Boolean) -> Unit) {
        callback(DrawerFolders.CustomFolder(context), true)
    }

    override fun createGroupHolder(parent: ViewGroup): FolderHolder {
        return FolderHolder(LayoutInflater.from(parent.context).inflate(R.layout.tab_item, parent, false))
    }

    override fun filterGroups() = groupsModel.getGroups()

    inner class FolderHolder(itemView: View) : GroupHolder(itemView)
}
