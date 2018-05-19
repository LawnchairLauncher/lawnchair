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