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

import android.graphics.RectF
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
                grid.numRows = currentValues.height
                grid.numColumns = currentValues.width
                grid.numHotseatIcons = currentValues.numHotseat
                grid.workspacePaddingLeftScale = currentValues.workspacePaddingScale.left
                grid.workspacePaddingRightScale = currentValues.workspacePaddingScale.right
                grid.workspacePaddingTopScale = currentValues.workspacePaddingScale.top
                grid.workspacePaddingBottomScale = currentValues.workspacePaddingScale.bottom
            }
            val paddings = RectF(idp.workspacePaddingLeftScale, idp.workspacePaddingTopScale, idp.workspacePaddingRightScale, idp.workspacePaddingBottomScale)
            setInitialValues(
                    CustomGridView.Values(idp.numRows, idp.numColumns, idp.numHotseatIcons, paddings))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        customGridView = view as CustomGridView
    }

    override fun onPause() {
        super.onPause()
        val idp = LauncherAppState.getIDP(context)
        val paddings = RectF(idp.workspacePaddingLeftScale, idp.workspacePaddingTopScale, idp.workspacePaddingRightScale, idp.workspacePaddingBottomScale)
        val oldValues = CustomGridView.Values(idp.numRows, idp.numColumns, idp.numHotseatIcons, paddings)
        val newValues = customGridView.currentValues
        if (oldValues != newValues) {
            val provider = CustomGridProvider.getInstance(context!!)
            provider.numRows = newValues.height
            provider.numColumns = newValues.width
            provider.numHotseatIcons = newValues.numHotseat
            provider.workspacePaddingLeftScale = newValues.workspacePaddingScale.left
            provider.workspacePaddingRightScale = newValues.workspacePaddingScale.right
            provider.workspacePaddingTopScale = newValues.workspacePaddingScale.top
            provider.workspacePaddingBottomScale = newValues.workspacePaddingScale.bottom
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_desktop_grid_size, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_reset_grid_size) {
            val idp = LauncherAppState.getIDP(context)
            customGridView.setValues(CustomGridView.Values(idp.numRowsOriginal, idp.numColumnsOriginal, idp.numHotseatIconsOriginal, RectF(1f, 1f, 1f, 1f)))
        }
        return super.onOptionsItemSelected(item)
    }
}
