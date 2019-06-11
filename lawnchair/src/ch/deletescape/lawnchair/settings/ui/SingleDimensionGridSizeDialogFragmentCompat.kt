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

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R

class SingleDimensionGridSizeDialogFragmentCompat : PreferenceDialogFragmentCompat(), SeekBar.OnSeekBarChangeListener, ColorEngine.OnColorChangeListener {

    private val gridSizePreference get() = preference as SingleDimensionGridSizePreference

    private var numRows = 0

    private val minValue = 3
    private val maxValue = 9

    private lateinit var numRowsPicker: SeekBar
    private lateinit var numRowsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val size = gridSizePreference.getSize()
        numRows = savedInstanceState?.getInt(SAVE_STATE_ROWS) ?: size
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        numRowsPicker = view.findViewById(R.id.numRowsPicker)
        numRowsLabel = view.findViewById(R.id.numRowsLabel)

        numRowsPicker.max = maxValue - minValue
        numRowsPicker.progress = numRows - minValue
        numRowsPicker.setOnSeekBarChangeListener(this)

        numRowsLabel.text = "${numRowsPicker.progress + minValue}"
        ColorEngine.getInstance(context!!).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            gridSizePreference.setSize(numRowsPicker.progress + minValue)
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setNeutralButton(R.string.theme_default, {_, _ ->
            gridSizePreference.setSize(0)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(SAVE_STATE_ROWS, numRowsPicker.progress)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        numRowsLabel.text = "${progress + minValue}"
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {

    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == ColorEngine.Resolvers.ACCENT) {
            val tintList = ColorStateList.valueOf(resolveInfo.color)
            numRowsPicker.apply {
                progressBackgroundTintList = tintList
                progressTintList = tintList
                thumbTintList = tintList
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

        fun newInstance(key: String?) = SingleDimensionGridSizeDialogFragmentCompat().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
        }
    }
}