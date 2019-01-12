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
import com.android.launcher3.ItemInfo
import com.android.launcher3.R
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ItemInfoMatcher

class AllAppsTabs(private val context: Context) : Iterable<AllAppsTabs.Tab> {

    val customTabs = ArrayList<Tab>()
    val customCount get() = customTabs.size
    val tabs = ArrayList<Tab>()
    val count get() = tabs.size

    var hasWorkApps = false
        set(value) {
            if (value != field) {
                field = value
                reloadTabs()
            }
        }

    val addedApps = ArrayList<ComponentKey>()

    init {
        loadCustomTabs()
        reloadTabs()
    }

    private fun reloadTabs() {
        tabs.clear()
        if (hasWorkApps) {
            tabs.add(PersonalTab(context, createMatcher(addedApps, personalMatcher)))
            tabs.add(WorkTab(context, createMatcher(addedApps, workMatcher)))
        } else {
            tabs.add(AllAppsTab(context, createMatcher(addedApps)))
        }
        tabs.addAll(customTabs)
    }

    private fun loadCustomTabs() {
        addedApps.clear()
        customTabs.clear()
        context.lawnchairPrefs.drawerTabs.getFolderInfos().forEach {
            it.contents.mapTo(addedApps) { app -> ComponentKey(app.targetComponent, app.user) }
            customTabs.add(Tab(it.title.toString(), createFolderMatcher(it.contents)))
        }
    }

    private fun createFolderMatcher(contents: List<ShortcutInfo>): ItemInfoMatcher {
        val components = contents.map { ComponentKey(it.targetComponent, it.user) }
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

    class AllAppsTab(context: Context, matcher: ItemInfoMatcher?) : Tab(R.string.all_apps_label, context, matcher)

    private val personalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle())!!
    private val workMatcher = ItemInfoMatcher.not(personalMatcher)!!

    inner class PersonalTab(context: Context, matcher: ItemInfoMatcher?) : Tab(R.string.all_apps_personal_tab, context, matcher)
    inner class WorkTab(context: Context, matcher: ItemInfoMatcher?) : Tab(R.string.all_apps_work_tab, context, matcher, true)

    open class Tab(val name: String, val matcher: ItemInfoMatcher?, val isWork: Boolean = false) {

        constructor(nameRes: Int, context: Context, matcher: ItemInfoMatcher?, isWork: Boolean = false) :
                this(context.getString(nameRes), matcher, isWork)
    }
}
