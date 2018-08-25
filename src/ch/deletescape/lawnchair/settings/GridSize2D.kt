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

class GridSize2D(
        prefs: LawnchairPreferences,
        rowsKey: String,
        columnsKey: String,
        targetObject: Any,
        onChangeListener: () -> Unit) : GridSize(prefs, rowsKey, targetObject, onChangeListener) {

    var numColumns by JavaField<Int>(targetObject, columnsKey)
    val numColumnsOriginal by JavaField<Int>(targetObject, "${columnsKey}Original")

    var numColumnsPref by prefs.IntPref("pref_$columnsKey", 0, onChange)

    init {
        applyNumColumns()
    }

    override fun applyCustomization() {
        super.applyCustomization()
        applyNumColumns()
    }

    private fun applyNumColumns() {
        numColumns = fromPref(numColumnsPref, numColumnsOriginal)
    }
}