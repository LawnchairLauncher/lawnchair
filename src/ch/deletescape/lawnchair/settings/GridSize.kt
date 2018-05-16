package ch.deletescape.lawnchair.settings

import ch.deletescape.lawnchair.LawnchairPreferences
import java.lang.reflect.Field
import kotlin.reflect.KProperty

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

    var numRowsPref by prefs.IntPref("pref_$rowsKey", 0, onChange)

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