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

package ch.deletescape.lawnchair.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserHandle
import ch.deletescape.lawnchair.LawnchairAppFilter
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import org.json.JSONArray
import org.json.JSONObject

class DrawerTabs(private val prefs: LawnchairPreferences) {

    private var tabsDataJson by prefs.StringPref("pref_drawerTabs", "[]", prefs.recreate)

    private val appMap = HashMap<ComponentName, LauncherActivityInfo>()
    private val tabs = ArrayList<FolderInfo>()
    private var tabsBuilt = false

    private val iconCache by lazy { LauncherAppState.getInstance(prefs.context).iconCache }

    val filteredApps = HashSet<ComponentName>()

    private val sortComparator = { it: ShortcutInfo -> it.title.toString().toLowerCase() }

    private fun buildTabList() {
        appMap.clear()
        tabs.clear()
        filteredApps.clear()

        LauncherAppsCompat.getInstance(prefs.context)
                .getActivityList(null, Process.myUserHandle()).forEach { app ->
            appMap[app.componentName] = app
        }

        val arr = JSONArray(tabsDataJson)
        (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .mapTo(tabs) {
                    FolderInfo().apply {
                        title = if (it.has(KEY_TITLE)) {
                            it.getString(KEY_TITLE)
                        } else {
                            ""
                        }
                        container = ItemInfo.NO_ID.toLong()
                        if (it.has(KEY_ITEMS)) {
                            val items = it.getJSONArray(KEY_ITEMS)
                            for (i in (0 until items.length())) {
                                val componentName = ComponentName.unflattenFromString(items.getString(i))
                                add(createInfo(componentName), false)
                                filteredApps.add(componentName)
                            }
                            contents.sortBy(sortComparator)
                        }
                    }
                }

        tabsBuilt = true
    }

    fun createInfo(componentName: ComponentName) = ShortcutInfo(AppInfo(
            prefs.context,
            appMap[componentName],
            Process.myUserHandle()
    ).apply { iconCache.getTitleAndIcon(this, false) })

    fun addItem(folderInfo: FolderInfo, item: ShortcutInfo) {
        folderInfo.add(item, false)
        folderInfo.contents.sortBy(sortComparator)
        saveToJson()
    }

    fun getFolder(position: Int) = tabs[position]

    fun getTabs(): List<FolderInfo> {
        if (!tabsBuilt)
            buildTabList()
        return tabs
    }

    fun setTabs(tabs: List<FolderInfo>) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
    }

    fun removeTab(position: Int) {
        tabs.removeAt(position)
        saveToJson()
    }

    fun addBlankTab() {
        tabs.add(FolderInfo().apply { title = "" })
        saveToJson()
    }

    fun saveToJson() {
        val arr = JSONArray()
        tabs.forEach {
            val items = JSONArray()
            it.contents.forEach { items.put(it.targetComponent.flattenToShortString()) }

            val obj = JSONObject()
            obj.put(KEY_TITLE, it.title)
            obj.put(KEY_ITEMS, items)

            arr.put(obj)
        }
        tabsDataJson = arr.toString()
    }

    fun getFolderInfos(): ArrayList<FolderInfo> {
        if (!tabsBuilt)
            buildTabList()
        return ArrayList(tabs)
    }

    companion object {

        const val KEY_TITLE = "title"
        const val KEY_ITEMS = "items"
    }

    open class TabAppFilter(context: Context) : LawnchairAppFilter(context) {

        private val filteredApps = Utilities.getLawnchairPrefs(context).drawerTabs.filteredApps

        override fun shouldShowApp(componentName: ComponentName?, user: UserHandle?): Boolean {
            return !filteredApps.contains(componentName) && super.shouldShowApp(componentName, user)
        }
    }
}
