/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui

import android.graphics.Point
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import ch.deletescape.lawnchair.settings.ui.preview.CustomGridProvider
import ch.deletescape.lawnchair.settings.ui.preview.CustomGridView
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

class DesktopGridSizeFragment : Fragment(), SettingsActivity.PreviewFragment {

    lateinit var customGridView: CustomGridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return CustomGridView(inflater.context, null).apply {
            val idp = LauncherAppState.getIDP(context)
            gridCustomizer = InvariantDeviceProfile.GridCustomizer { grid ->
                grid.numRows = currentSize.y
                grid.numColumns = currentSize.x
            }
            setInitialSize(Point(idp.numColumns, idp.numRows))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        customGridView = view as CustomGridView
    }

    override fun onPause() {
        super.onPause()
        val idp = LauncherAppState.getIDP(context)
        val oldSize = Point(idp.numColumns, idp.numRows)
        val newSize = customGridView.currentSize
        if (oldSize != newSize) {
            val provider = CustomGridProvider.getInstance(context!!)
            provider.numRows = newSize.y
            provider.numColumns = newSize.x
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_desktop_grid_size, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_reset_grid_size) {
            val idp = LauncherAppState.getIDP(context)
            customGridView.setSize(Point(idp.numColumnsOriginal, idp.numRowsOriginal))
        }
        return super.onOptionsItemSelected(item)
    }
}
