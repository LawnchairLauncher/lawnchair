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

package ch.deletescape.lawnchair.preferences

import android.app.AlertDialog
import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import ch.deletescape.lawnchair.addOrRemove
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.groups.DrawerTabs
import ch.deletescape.lawnchair.groups.ui.AppCategorizationFragment
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

class MultiSelectTabPreference(context: Context, attrs: AttributeSet?) : LauncherRecyclerViewPreference(context, attrs) {

    lateinit var componentKey: ComponentKey
    private val selections = mutableMapOf<DrawerTabs.CustomTab, Boolean>()
    private val tabs = context.lawnchairPrefs.drawerTabs.getGroups().mapNotNull { it as? DrawerTabs.CustomTab }
    var edited = false
        private set(value) {
            field = value
        }

    override fun onBindRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = Adapter()
        recyclerView.layoutManager = LinearLayoutManager(themedContext)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setPositiveButton(R.string.action_apply) { _, _ ->
            tabs.forEach {
                if (it.contents.value().addOrRemove(componentKey, selections[it] == true)) {
                    edited = true
                }
            }
            selections.clear()
            loadSummary()
        }
        builder.setNeutralButton(R.string.tabs_manage) { _, _ ->
            SettingsActivity.startFragment(context, AppCategorizationFragment::class.java.name, R.string.categorization)
        }
        builder.setOnDismissListener {
            selections.clear()
            loadSummary()
        }
    }

    fun loadSummary() {
        val added = tabs
                .filter { it.contents.value().contains(componentKey) }
                .map { it.getTitle() }
        summary = if (!added.isEmpty()) {
            TextUtils.join(", ", added)
        } else {
            context.getString(R.string.none)
        }
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {

        init {
            tabs.forEach {
                selections[it] = it.contents.value().contains(componentKey)
            }
        }

        override fun getItemCount() = tabs.count()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.tab_item_check, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(tabs[position])
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            private val textView = (itemView as CheckedTextView).apply {
                applyAccent()
            }
            private var checked = false
                set(value) {
                    field = value
                    textView.isChecked = value
                }

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(tab: DrawerTabs.CustomTab) {
                textView.text = tab.getTitle()
                checked = selections[tab] == true
            }

            override fun onClick(v: View) {
                checked = !checked
                val tab = tabs[adapterPosition]
                selections[tab] = checked
            }
        }
    }
}
