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