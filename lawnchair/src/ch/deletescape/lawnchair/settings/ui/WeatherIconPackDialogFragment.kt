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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.smartspace.weather.icons.WeatherIconManager
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

// TODO: why in the world can't I manage to theme the buttons?!
class WeatherIconPackDialogFragment: PreferenceDialogFragmentCompat() {
    private lateinit var manager: WeatherIconManager
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = WeatherIconManager.getInstance(context!!)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNeutralButton(R.string.get_more_icon_packs) { dialog, _ ->
            activity?.startActivity(PackageManagerHelper.getMarketSearchIntent(activity, "Chronus Weather Icons"))
            dialog.dismiss()
        }.setPositiveButton(null, null)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        list = view.findViewById(R.id.pack_list)
        list.adapter = WeatherIconPackAdapter(context!!, manager.getIconPacks(), manager.getPack().pkgName) {
            context?.lawnchairPrefs?.weatherIconPack = it
            dismiss()
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {

    }

    inner class WeatherIconPackAdapter(context: Context, val packs: List<WeatherIconManager.WeatherIconPack>, private val selected: String, private val onSelect: (String) -> Unit) :
            ArrayAdapter<WeatherIconManager.WeatherIconPack>(context, R.layout.weather_icon_pack_dialog_item, 0, packs) {
        private val color = ColorEngine.getInstance(context).accent
        private val showDebug = context.lawnchairPrefs.showDebugInfo

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return View.inflate(context, R.layout.weather_icon_pack_dialog_item, null).apply {
                val pack = packs[position]
                findViewById<ImageView>(android.R.id.icon).setImageDrawable(pack.icon)
                findViewById<TextView>(android.R.id.title).text = pack.name
                if (showDebug) {
                    findViewById<TextView>(android.R.id.summary).apply {
                        text = pack.pkgName
                        isVisible = true
                    }
                }
                findViewById<RadioButton>(R.id.select).apply {
                    isChecked = pack.pkgName == selected
                    setOnCheckedChangeListener { _, _ ->
                        onSelect(pack.pkgName)
                    }
                    applyColor(color)
                }
                setOnClickListener {
                    onSelect(pack.pkgName)
                }
            }
        }
    }

    companion object {
        fun newInstance() = WeatherIconPackDialogFragment().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, WeatherIconPackPreference.KEY)
            }
        }
    }
}