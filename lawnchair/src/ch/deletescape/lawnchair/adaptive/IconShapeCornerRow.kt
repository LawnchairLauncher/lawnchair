/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.adaptive

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SimpleAdapter
import com.android.launcher3.R
import kotlinx.android.synthetic.lawnchair.icon_shape_corner_row.view.*

class IconShapeCornerRow(context: Context, attrs: AttributeSet?) :
        LinearLayout(context, attrs), SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemSelectedListener {

    private var listener: ((IconShape.Corner) -> Unit)? = null
    private var scale = 1f
        set(value) {
            if (field != value) {
                field = value
                updateSeekBar()
                notifyChanged()
            }
        }
    private var shape = "arc"
        set(value) {
            if (field != value) {
                field = value
                updateSpinner()
                notifyChanged()
            }
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        spinner.adapter = CornerShapeAdapter(context)
        spinner.onItemSelectedListener = this
        updateSeekBar()
        updateSpinner()
        seekbar.max = 10
        seekbar.setOnSeekBarChangeListener(this)
    }

    fun init(titleRes: Int, corner: IconShape.Corner, listener: (IconShape.Corner) -> Unit) {
        title.setText(titleRes)
        this.scale = corner.scale.x
        this.shape = corner.shape.toString()
        this.listener = listener
    }

    @SuppressLint("SetTextI18n")
    private fun updateSeekBar() {
        seekbar.progress = (scale * 10).toInt()
        txtValue.text = "${(scale * 100).toInt()}%"
    }

    private fun updateSpinner() {
        for (i in 0 until spinner.adapter.count) {
            val itemMap = spinner.adapter.getItem(i) as Map<*, *>
            if (itemMap["value"] == shape) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun notifyChanged() {
        listener?.invoke(IconShape.Corner(IconCornerShape.fromString(shape), scale))
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        scale = progress / 10f
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {

    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        shape = (parent.adapter.getItem(position) as Map<*, *>)["value"] as String
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    class CornerShapeAdapter(context: Context) :
            SimpleAdapter(context, getEntries(context),
                          android.R.layout.simple_list_item_1,
                          arrayOf("text"),
                          intArrayOf(android.R.id.text1)) {

        companion object {

            private fun getEntries(context: Context): List<Map<String, String>> {
                return listOf(
                        mapOf(
                                "value" to "arc",
                                "text" to context.getString(R.string.icon_shape_corner_round)
                             ),
                        mapOf(
                                "value" to "squircle",
                                "text" to context.getString(R.string.icon_shape_corner_squircle)
                             ),
                        mapOf(
                                "value" to "cut",
                                "text" to context.getString(R.string.icon_shape_corner_cut)
                             )
                             )
            }
        }
    }
}
