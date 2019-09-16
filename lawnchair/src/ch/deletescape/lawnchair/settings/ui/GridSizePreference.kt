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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import ch.deletescape.lawnchair.settings.ui.preview.CustomGridProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities

class GridSizePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    private val customGrid = CustomGridProvider.getInstance(context)
    private val idp = LauncherAppState.getIDP(context)

    init {
        updateSummary()
    }

    fun getSize(): Pair<Int, Int> {
        val rows = getValue(customGrid.numRows, idp.numRowsOriginal)
        val columns = getValue(customGrid.numColumns, idp.numColumnsOriginal)
        return Pair(rows, columns)
    }

    fun setSize(rows: Int, columns: Int) {
        customGrid.numRows = rows
        customGrid.numColumns = columns
        updateSummary()
    }

    private fun updateSummary() {
        val value = getSize()
        summary = "${value.first}x${value.second}"
    }

    override fun getDialogLayoutResource() = R.layout.pref_dialog_grid_size

    private fun getValue(value: Int, default: Int): Int {
        return if (value > 0) value else default
    }
}