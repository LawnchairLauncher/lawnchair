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

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.util.Log
import android.view.View
import android.widget.NumberPicker
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R

class GridSizeDialogFragmentCompat : PreferenceDialogFragmentCompat(), ColorEngine.OnColorChangeListener {

    private val gridSizePreference get() = preference as GridSizePreference

    private var numRows = 0
    private var numColumns = 0

    private lateinit var numRowsPicker: NumberPicker
    private lateinit var numColumnsPicker: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val size = gridSizePreference.getSize()
        numRows = savedInstanceState?.getInt(SAVE_STATE_ROWS) ?: size.first
        numColumns = savedInstanceState?.getInt(SAVE_STATE_COLUMNS) ?: size.second
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        numRowsPicker = view.findViewById(R.id.rowsPicker)
        numColumnsPicker = view.findViewById(R.id.columnsPicker)

        numRowsPicker.minValue = 3
        numRowsPicker.maxValue = 20
        numColumnsPicker.minValue = 3
        numColumnsPicker.maxValue = 20

        numRowsPicker.value = numRows
        numColumnsPicker.value = numColumns

        ColorEngine.getInstance(context!!).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            gridSizePreference.setSize(numRowsPicker.value, numColumnsPicker.value)
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setNeutralButton(R.string.theme_default, {_, _ ->
            gridSizePreference.setSize(0, 0)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(SAVE_STATE_ROWS, numRowsPicker.value)
        outState.putInt(SAVE_STATE_COLUMNS, numColumnsPicker.value)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == ColorEngine.Resolvers.ACCENT) {
            try {
                val mSelectionDivider = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
                mSelectionDivider.isAccessible = true
                val drawable = mSelectionDivider.get(numColumnsPicker) as Drawable
                drawable.setTint(resolveInfo.color)
                mSelectionDivider.set(numColumnsPicker, drawable)
                mSelectionDivider.set(numRowsPicker, drawable)
            } catch (e: Exception) {
                Log.e("GridSizeDialog", "Failed to set mSelectionDivider", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).applyAccent()
    }

    override fun onDetach() {
        super.onDetach()
        ColorEngine.getInstance(context!!).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    companion object {
        const val SAVE_STATE_ROWS = "rows"
        const val SAVE_STATE_COLUMNS = "columns"

        fun newInstance(key: String?) = GridSizeDialogFragmentCompat().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
        }
    }
}