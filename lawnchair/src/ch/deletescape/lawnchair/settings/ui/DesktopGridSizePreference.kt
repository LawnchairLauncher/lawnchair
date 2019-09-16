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
import androidx.preference.Preference
import ch.deletescape.lawnchair.preferences.ResumablePreference
import ch.deletescape.lawnchair.settings.ui.preview.CustomGridProvider
import com.android.launcher3.LauncherAppState

class DesktopGridSizePreference(context: Context, attrs: AttributeSet?) :
        Preference(context, attrs), ResumablePreference {

    private val customGrid = CustomGridProvider.getInstance(context)
    private val idp = LauncherAppState.getIDP(context)

    init {
        fragment = DesktopGridSizeFragment::class.java.name
    }

    fun getSize(): Pair<Int, Int> {
        val rows = getValue(customGrid.numRows, idp.numRowsOriginal)
        val columns = getValue(customGrid.numColumns, idp.numColumnsOriginal)
        return Pair(rows, columns)
    }

    private fun updateSummary() {
        val value = getSize()
        summary = "${value.first}x${value.second}"
    }

    override fun onResume() {
        updateSummary()
    }

    private fun getValue(value: Int, default: Int): Int {
        return if (value > 0) value else default
    }
}