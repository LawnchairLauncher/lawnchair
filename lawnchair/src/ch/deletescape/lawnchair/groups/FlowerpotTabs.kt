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
import ch.deletescape.lawnchair.flowerpot.Flowerpot
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
                title.value = beautifyName(it.name)
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

    class FlowerpotTab(context: Context) : Tab(context, TYPE_FLOWERPOT, R.string.default_tab_name) {

        val potName = StringCustomization("potName", "")

        private val pot by lazy { Flowerpot.Manager.getInstance(context).getPot(potName.value!!, true)!! }

        init {
            addCustomization(potName)

            customizations.setOrder(KEY_TITLE, KEY_COLOR)
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
    }

    companion object {

        const val TYPE_FLOWERPOT = 4

        private fun beautifyName(name: String): String {
            return name.replace('_', ' ').toLowerCase().capitalize()
        }
    }
}
