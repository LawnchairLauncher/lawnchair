/*
 * Copyright 2016 Priyesh Patel
 * Copyright 2019 Lawnchair Launcher
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


import android.app.Dialog
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import ch.deletescape.lawnchair.applyAccent
import com.android.launcher3.R
import me.priyesh.chroma.*

class ColorPickerDialog : DialogFragment() {

    companion object {
        private const val ARG_KEY = "arg_key"
        private const val ARG_INITIAL_COLOR = "arg_initial_color"
        private const val ARG_COLOR_MODE = "arg_color_mode_name"
        private const val ARG_RESOLVERS = "arg_resolvers"

        @JvmStatic
        fun newInstance(key: String, @ColorInt initialColor: Int, colorMode: ColorMode, resolvers: Array<String>): ColorPickerDialog {
            val fragment = ColorPickerDialog()
            fragment.arguments = makeArgs(key, initialColor, colorMode, resolvers)
            return fragment
        }

        @JvmStatic
        private fun makeArgs(key: String, @ColorInt initialColor: Int, colorMode: ColorMode, resolvers: Array<String>): Bundle {
            val args = Bundle()
            args.putString(ARG_KEY, key)
            args.putInt(ARG_INITIAL_COLOR, initialColor)
            args.putString(ARG_COLOR_MODE, colorMode.name)
            args.putStringArray(ARG_RESOLVERS, resolvers)
            return args
        }
    }

    private var listener: ColorSelectListener? = null
    private lateinit var tabbedPickerView: TabbedPickerView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = context!!
        val key = savedInstanceState?.getString(ARG_KEY)
                ?: arguments!!.getString(ARG_KEY, "pref_accentColorResolver")
        val initialColor = savedInstanceState?.getInt(ARG_INITIAL_COLOR, ChromaView.DefaultColor)
                ?: arguments!!.getInt(ARG_INITIAL_COLOR)
        val resolvers = savedInstanceState?.getStringArray(ARG_RESOLVERS)
                ?: arguments!!.getStringArray(ARG_RESOLVERS)
        val colorMode = ColorMode.fromName(savedInstanceState?.getString(ARG_COLOR_MODE)
                ?: arguments!!.getString(ARG_COLOR_MODE, ColorMode.RGB.name))

        tabbedPickerView = TabbedPickerView.fromPrefs(context, key, initialColor, colorMode, resolvers, ::dismiss)
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
                window?.setLayout(width, height)

                // for some reason it won't respect the windowBackground attribute in the theme
                window?.setBackgroundDrawable(context.getDrawable(R.drawable.dialog_material_background))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = (dialog as AlertDialog)
        dialog.applyAccent()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(makeArgs(tabbedPickerView.key, tabbedPickerView.chromaView.currentColor, tabbedPickerView.chromaView.colorMode, tabbedPickerView.resolvers))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener = null
    }
}
