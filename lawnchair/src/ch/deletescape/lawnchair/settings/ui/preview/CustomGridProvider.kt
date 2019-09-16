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

package ch.deletescape.lawnchair.settings.ui.preview

import android.content.Context
import android.graphics.Bitmap
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.graphics.LauncherPreviewRenderer
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.UiThreadHelper
import java.util.concurrent.Future

class CustomGridProvider(private val context: Context) : InvariantDeviceProfile.GridCustomizer {

    private val prefs = context.lawnchairPrefs
    var numRows by prefs.IntPref(KEY_NUM_ROWS, -1)
    var numColumns by prefs.IntPref(KEY_NUM_COLUMNS, -1)

    override fun customizeGrid(grid: InvariantDeviceProfile.GridOverrides) {
        if (numRows > 0) {
            grid.numRows = numRows
        }
        if (numColumns > 0) {
            grid.numColumns = numColumns
        }
    }

    fun renderPreview(customizer: InvariantDeviceProfile.GridCustomizer? = null): Future<Bitmap> {
        val idp = InvariantDeviceProfile(context) { grid ->
            customizeGrid(grid)
            customizer?.customizeGrid(grid)
        }
        val executor = LooperExecutor(UiThreadHelper.getBackgroundLooper())
        return executor.submit(LauncherPreviewRenderer(context, idp))
    }

    companion object : LawnchairSingletonHolder<CustomGridProvider>(::CustomGridProvider) {

        private const val KEY_NUM_ROWS = "pref_numRows"
        private const val KEY_NUM_COLUMNS = "pref_numColumns"

        val GRID_CUSTOMIZATIONS_PREFS = arrayOf(
                KEY_NUM_ROWS,
                KEY_NUM_COLUMNS
                                                     )
    }
}
