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

import android.view.ViewGroup
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon

class DrawerFolderItem(private val info: DrawerFolderInfo, private val index: Int) {

    private var icon: FolderIcon? = null

    fun getFolderIcon(launcher: Launcher, container: ViewGroup): FolderIcon {
        if (icon == null) {
            icon = FolderIcon.fromXml(R.layout.all_apps_folder_icon, launcher, container, info)
        }
        return icon!!.apply {
            (parent as? ViewGroup)?.removeView(this)
        }
    }
}
