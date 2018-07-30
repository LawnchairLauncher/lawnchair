/*
 * Copyright 2016 Priyesh Patel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.colors.preferences

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.app.DialogFragment
import android.view.WindowManager
import ch.deletescape.lawnchair.colors.ColorEngine
import me.priyesh.chroma.*
import kotlin.properties.Delegates

class ColorPickerDialog : DialogFragment() {

    companion object {
        private val ArgInitialColor = "arg_initial_color"
        private val ArgColorModeName = "arg_color_mode_name"

        @JvmStatic
        fun newInstance(@ColorInt initialColor: Int): ColorPickerDialog {
            val fragment = ColorPickerDialog()
            fragment.arguments = makeArgs(initialColor)
            return fragment
        }

        @JvmStatic
        private fun makeArgs(@ColorInt initialColor: Int): Bundle {
            val args = Bundle()
            args.putInt(ArgInitialColor, initialColor)
            args.putString(ArgColorModeName, ColorMode.RGB.name)
            return args
        }
    }

    private var listener: ColorSelectListener? = null
    private lateinit var tabbedPickerView: TabbedPickerView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = context!!
        val initialColor = savedInstanceState?.getInt(ArgInitialColor, ChromaView.DefaultColor) ?: arguments!!.getInt(ArgInitialColor)

        tabbedPickerView = TabbedPickerView(context, initialColor, ::dismiss)
        return AlertDialog.Builder(context).setView(tabbedPickerView).create().apply {
            setOnShowListener {
                val width: Int; val height: Int
                if (orientation(context) == ORIENTATION_LANDSCAPE) {
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    width = 80 percentOf screenDimensions(context).widthPixels
                } else {
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    width = resources.getDimensionPixelSize(R.dimen.chroma_dialog_width)
                }
                window.setLayout(width, height)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(makeArgs(tabbedPickerView.chromaView.currentColor))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener = null
    }
}
