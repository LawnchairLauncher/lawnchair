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

package ch.deletescape.lawnchair.allapps

import android.content.ComponentName
import android.content.Context
import android.os.Process
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.groups.DrawerTabs
import ch.deletescape.lawnchair.groups.FlowerpotTabs
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ItemInfoMatcher

class AllAppsTabs(private val context: Context) : Iterable<AllAppsTabs.Tab> {

    val tabs = ArrayList<Tab>()
    val count get() = tabs.size

    var hasWorkApps = false
        set(value) {
            if (value != field) {
                field = value
                reloadTabs()
            }
        }

    private val addedApps = ArrayList<ComponentKey>()

    init {
        reloadTabs()
    }

    fun reloadTabs() {
        addedApps.clear()
        tabs.clear()
        context.lawnchairPrefs.currentTabsModel.getGroups().mapNotNullTo(tabs) {
            when {
                hasWorkApps && it is DrawerTabs.PersonalTab ->
                    PersonalTab(createMatcher(addedApps, personalMatcher), drawerTab = it)
                hasWorkApps && it is DrawerTabs.WorkTab ->
                    WorkTab(createMatcher(addedApps, workMatcher), drawerTab = it)
                !hasWorkApps && it is DrawerTabs.AllAppsTab ->
                    AllAppsTab(createMatcher(addedApps), drawerTab = it)
                it is DrawerTabs.CustomTab -> {
                    if (it.hideFromAllApps.value()) {
                        addedApps.addAll(it.contents.value())
                    }
                    Tab(it.getTitle(), it.getFilter(context).matcher, drawerTab = it)
                }
                it is FlowerpotTabs.FlowerpotTab && !it.getMatches().isEmpty() -> {
                    addedApps.addAll(it.getMatches())
                    Tab(it.getTitle(), it.getFilter(context).matcher, drawerTab = it)
                }
                else -> null
            }
        }
    }

    private fun createTabMatcher(components: Set<ComponentKey>): ItemInfoMatcher {
        return object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?): Boolean {
                return components.contains(ComponentKey(info.targetComponent, info.user))
            }
        }
    }

    private fun createMatcher(components: List<ComponentKey>, base: ItemInfoMatcher? = null): ItemInfoMatcher {
        return object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?): Boolean {
                if (base?.matches(info, cn) == false) return false
                return !components.contains(ComponentKey(info.targetComponent, info.user))
            }
        }
    }

    override fun iterator(): Iterator<Tab> {
        return tabs.iterator()
    }

    operator fun get(index: Int) = tabs[index]

    class AllAppsTab(matcher: ItemInfoMatcher?, drawerTab: DrawerTabs.Tab)
        : Tab(drawerTab.getTitle(), matcher, drawerTab = drawerTab)

    private val personalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle())!!
    private val workMatcher = ItemInfoMatcher.not(personalMatcher)!!

    inner class PersonalTab(matcher: ItemInfoMatcher?, drawerTab: DrawerTabs.Tab)
        : Tab(drawerTab.getTitle(), matcher, drawerTab = drawerTab)
    inner class WorkTab(matcher: ItemInfoMatcher?, drawerTab: DrawerTabs.Tab)
        : Tab(drawerTab.getTitle(), matcher, true, drawerTab)

    open class Tab(val name: String, val matcher: ItemInfoMatcher?,
                   val isWork: Boolean = false, val drawerTab: DrawerTabs.Tab)
}
