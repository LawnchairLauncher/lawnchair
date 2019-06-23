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

package ch.deletescape.lawnchair.groups

import android.content.Context
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ch.deletescape.lawnchair.allapps.AllAppsTabsController
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.flowerpot.Flowerpot
import ch.deletescape.lawnchair.getLauncherOrNull
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.util.ThemedContextProvider
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

class FlowerpotTabs(manager: AppGroupsManager) : DrawerTabs(manager, AppGroupsManager.CategorizationType.Flowerpot) {

    private val flowerpotManager = Flowerpot.Manager.getInstance(context)

    init {
        val pots = flowerpotManager.getAllPots().toMutableSet()
        val existingGroups = getGroups().filter { group ->
            if (group !is FlowerpotTab) {
                true
            } else {
                val pot = pots.firstOrNull { pot -> pot.name == group.potName.value }
                pot?.let { pots.remove(it) }
                pot != null
            }
        }.toMutableList()
        existingGroups.addAll(pots.map {
            FlowerpotTab(context).apply {
                title.value = it.displayName
                potName.value = it.name
            }
        })
        setGroups(existingGroups)
        saveToJson()
    }

    override fun getGroupCreator(type: Int): GroupCreator<Tab> {
        return when (type) {
            TYPE_FLOWERPOT -> ::FlowerpotTab
            else -> super.getGroupCreator(type)
        }
    }

    class FlowerpotTab(private val context: Context) : Tab(context, TYPE_FLOWERPOT, R.string.default_tab_name) {
        // todo: make updating the title dynamically less hacky (aka make it actually work)
        val potName: FlowerpotCustomization = FlowerpotCustomization(KEY_FLOWERPOT, DEFAULT, context, customizations.entries.first { it is CustomTitle } as CustomTitle)

        private val pot
            get() = Flowerpot.Manager.getInstance(context).getPot(potName.value ?: DEFAULT, true)!!

        init {
            addCustomization(potName)

            customizations.setOrder(KEY_TITLE, KEY_FLOWERPOT, KEY_COLOR)
        }

        override fun getSummary(context: Context): String? {
            val size = getFilter(context).size
            return context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
        }

        fun getMatches(): Set<ComponentKey> {
            pot.ensureLoaded()
            return pot.apps.matches
        }

        fun getFilter(context: Context): Filter<*> {
            return CustomFilter(context, getMatches())
        }

        companion object {
            const val DEFAULT = "PERSONALIZATION"
            const val KEY_FLOWERPOT = "potName"
        }
    }

    class FlowerpotCustomization(key: String, default: String, private val context: Context, private val title: Group.CustomTitle) : Group.StringCustomization(key, default) {
        private val flowerpotManager = Flowerpot.Manager.getInstance(context)
        private val displayName
            get() = flowerpotManager.getPot(value ?: default)?.displayName

        override fun createRow(context: Context, parent: ViewGroup, accent: Int): View? {
            val view = LayoutInflater.from(context).inflate(R.layout.drawer_tab_flowerpot_row, parent, false)
            updateSummary(view)

            view.setOnClickListener {
                // make a copy to ensure indexes don't change while the dialog is opened
                val pots = flowerpotManager.getAllPots().toList()
                val currentIndex = pots.indexOfFirst { it.name == value ?: default }
                val themedContext = ThemedContextProvider(context, null, ThemeOverride.Settings()).get()
                AlertDialog.Builder(themedContext, ThemeOverride.AlertDialog().getTheme(context))
                        .setTitle(R.string.pref_appcategorization_flowerpot_title)
                        .setSingleChoiceItems(pots.map { it.displayName }.toTypedArray(), currentIndex) { dialog, which ->
                            if (currentIndex != which) {
                                var updateTitle = false
                                // Update the group title if it exactly matched the previous category name
                                if (title.value == displayName) {
                                    updateTitle = true
                                }
                                value = pots[which].name
                                if (updateTitle) {
                                    title.value = displayName
                                }
                                updateSummary(view)
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                        .apply {
                            applyAccent()
                            show()
                        }
            }

            return view
        }

        override fun saveToJson(context: Context): String? {
            return value ?: default
        }

        private fun updateSummary(view: View) {
            view.findViewById<TextView>(R.id.current_category).setText(displayName)
        }

        override fun clone(): Group.Customization<String, String> {
            return FlowerpotCustomization(key, default, context, title).also { it.value = value }
        }
    }

    companion object {

        const val TYPE_FLOWERPOT = 4
    }
}
