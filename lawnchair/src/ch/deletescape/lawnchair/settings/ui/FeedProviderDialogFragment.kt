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
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceDialogFragmentCompat
import ch.deletescape.lawnchair.FeedBridge
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.R

// TODO: why in the world can't I manage to theme the buttons?!
class FeedProviderDialogFragment : PreferenceDialogFragmentCompat() {
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        // builder.setNeutralButton(R.string.get_more_icon_packs) { dialog, _ ->
        //    activity?.startActivity(PackageManagerHelper.getMarketSearchIntent(activity, "Lawnchair Feed Provider"))
        //    dialog.dismiss()
        // }.setPositiveButton(null, null)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        list = view.findViewById(R.id.pack_list)
        list.adapter = FeedProviderAdapter(context!!, FeedProviderPreference.providers(context!!), context!!.lawnchairPrefs.feedProvider) {
                    context?.lawnchairPrefs?.feedProvider = it
                    dismiss()
                }
    }

    override fun onDialogClosed(positiveResult: Boolean) {

    }

    inner class FeedProviderAdapter(context: Context, val providers: List<FeedProviderPreference.ProviderInfo>,
                                    private val selected: String,
                                    private val onSelect: (String) -> Unit) :
            ArrayAdapter<FeedProviderPreference.ProviderInfo>(context, R.layout.weather_icon_pack_dialog_item, 0,
                                                              providers) {
        private val color = ColorEngine.getInstance(context).accent
        private val showDebug = context.lawnchairPrefs.showDebugInfo

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return View.inflate(context, R.layout.weather_icon_pack_dialog_item, null).apply {
                val provider = providers[position]
                findViewById<ImageView>(android.R.id.icon).setImageDrawable(provider.icon)
                findViewById<TextView>(android.R.id.title).text = provider.name
                if (showDebug) {
                    findViewById<TextView>(android.R.id.summary).apply {
                        text = provider.packageName
                        isVisible = true
                    }
                }
                findViewById<RadioButton>(R.id.select).apply {
                    isChecked = provider.packageName == selected
                    setOnCheckedChangeListener { _, _ ->
                        onSelect(provider.packageName)
                    }
                    applyColor(color)
                }
                setOnClickListener {
                    onSelect(provider.packageName)
                }
            }
        }
    }

    companion object {
        fun newInstance() = FeedProviderDialogFragment().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, FeedProviderPreference.KEY)
            }
        }
    }
}