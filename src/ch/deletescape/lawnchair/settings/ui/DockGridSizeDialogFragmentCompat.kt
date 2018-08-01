package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R

class DockGridSizeDialogFragmentCompat : PreferenceDialogFragmentCompat(), SeekBar.OnSeekBarChangeListener, ColorEngine.OnAccentChangeListener {

    private val gridSizePreference get() = preference as DockGridSizePreference

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
        ColorEngine.getInstance(context!!).addAccentChangeListener(this)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            gridSizePreference.setSize(numRowsPicker.progress + minValue)
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setNeutralButton(R.string.grid_size_default, {_, _ ->
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

    override fun onAccentChange(color: Int, foregroundColor: Int) {
        val tintList = ColorStateList.valueOf(color)
        numRowsPicker.apply {
            progressBackgroundTintList = tintList
            progressTintList = tintList
            thumbTintList = tintList
        }
    }

    override fun onDetach() {
        super.onDetach()
        ColorEngine.getInstance(context!!).removeAccentChangeListener(this)
    }

    companion object {
        const val SAVE_STATE_ROWS = "rows"

        fun newInstance(key: String?) = DockGridSizeDialogFragmentCompat().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
        }
    }
}