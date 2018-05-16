package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import com.android.launcher3.R
import com.android.launcher3.Utilities

class DockGridSizePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    val gridSize = Utilities.getLawnchairPrefs(context).dockGridSize
    val defaultSize by lazy { gridSize.numRowsOriginal }

    init {
        updateSummary()
    }

    fun getSize(): Int {
        return gridSize.fromPref(gridSize.numRows, defaultSize)
    }

    fun setSize(rows: Int) {
        gridSize.numRowsPref = gridSize.toPref(rows, defaultSize)
        updateSummary()
    }

    private fun updateSummary() {
        val value = getSize()
        summary = "$value"
    }

    override fun getDialogLayoutResource() = R.layout.pref_dialog_dock_grid_size
}