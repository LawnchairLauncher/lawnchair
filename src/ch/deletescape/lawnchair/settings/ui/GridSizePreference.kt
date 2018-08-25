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
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import com.android.launcher3.R
import com.android.launcher3.Utilities

class GridSizePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    val gridSize = Utilities.getLawnchairPrefs(context).gridSize
    val defaultSize by lazy { Pair(gridSize.numRowsOriginal, gridSize.numColumnsOriginal) }

    init {
        updateSummary()
    }

    fun getSize(): Pair<Int, Int> {
        val rows = gridSize.fromPref(gridSize.numRows, defaultSize.first)
        val columns = gridSize.fromPref(gridSize.numColumns, defaultSize.second)
        return Pair(rows, columns)
    }

    fun setSize(rows: Int, columns: Int) {
        gridSize.numRowsPref = gridSize.toPref(rows, defaultSize.first)
        gridSize.numColumnsPref = gridSize.toPref(columns, defaultSize.second)
        updateSummary()
    }

    private fun updateSummary() {
        val value = getSize()
        summary = "${value.first}x${value.second}"
    }

    override fun getDialogLayoutResource() = R.layout.pref_dialog_grid_size
}