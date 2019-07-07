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

package ch.deletescape.lawnchair.settings

import ch.deletescape.lawnchair.JavaField
import ch.deletescape.lawnchair.LawnchairPreferences

open class GridSize(
        prefs: LawnchairPreferences,
        rowsKey: String,
        targetObject: Any,
        private val onChangeListener: () -> Unit) {

    var numRows by JavaField<Int>(targetObject, rowsKey)
    val numRowsOriginal by JavaField<Int>(targetObject, "${rowsKey}Original")

    protected val onChange = {
        applyCustomization()
        onChangeListener.invoke()
    }

    var numRowsPref by prefs.StringIntMigrationPref("pref_$rowsKey", 0, onChange)

    init {
        applyNumRows()
    }

    protected open fun applyCustomization() {
        applyNumRows()
    }

    private fun applyNumRows() {
        numRows = fromPref(numRowsPref, numRowsOriginal)
    }

    fun fromPref(value: Int, default: Int) = if (value != 0) value else default
    fun toPref(value: Int, default: Int) = if (value != default) value else 0
}