package ch.deletescape.lawnchair.settings

import ch.deletescape.lawnchair.LawnchairPreferences
import java.lang.reflect.Field
import kotlin.reflect.KProperty

class GridSize(
        private val prefs: LawnchairPreferences,
        rowsKey: String,
        columnsKey: String,
        targetObject: Any) {

    var numRows by JavaField<Int>(targetObject, rowsKey)
    var numColumns by JavaField<Int>(targetObject, columnsKey)
    val numRowsOriginal by JavaField<Int>(targetObject, "${rowsKey}Original")
    val numColumnsOriginal by JavaField<Int>(targetObject, "${columnsKey}Original")

    private val onChange = {
        applyCustomization()
        prefs.refreshGrid()
    }

    var numRowsPref by prefs.IntPref("pref_$rowsKey", 0, onChange)
    var numColumnsPref by prefs.IntPref("pref_$columnsKey", 0, onChange)

    init {
        applyCustomization()
    }

    private fun applyCustomization() {
        numRows = fromPref(numRowsPref, numRowsOriginal)
        numColumns = fromPref(numColumnsPref, numColumnsOriginal)
    }

    fun fromPref(value: Int, default: Int) = if (value != 0) value else default
    fun toPref(value: Int, default: Int) = if (value != default) value else 0

    @Suppress("UNCHECKED_CAST")
    class JavaField<T>(private val targetObject: Any, fieldName: String) {

        private val field: Field = targetObject.javaClass.getField(fieldName)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return field.get(targetObject) as T
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            field.set(targetObject, value)
        }
    }
}