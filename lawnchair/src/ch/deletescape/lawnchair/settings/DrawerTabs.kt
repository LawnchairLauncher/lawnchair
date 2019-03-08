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

import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.util.ComponentKey
import org.json.JSONArray
import org.json.JSONObject

class DrawerTabs(prefs: LawnchairPreferences) {

    private var tabsDataJson by prefs.StringPref("pref_drawerTabs", "[]", prefs.recreate)
    private val tabs = ArrayList<Tab>()

    private val context = prefs.context

    init {
        val arr = JSONArray(tabsDataJson)
        (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .mapTo(tabs) {
                    val title = if (it.has(KEY_TITLE)) it.getString(KEY_TITLE) else ""
                    val hideFromAllApps = if (it.has(KEY_HIDE_FROM_ALL_APPS))
                        it.getBoolean(KEY_HIDE_FROM_ALL_APPS) else true
                    val contents = mutableSetOf<ComponentKey>()
                    if (it.has(KEY_ITEMS)) {
                        val rawItems = it.getJSONArray(KEY_ITEMS)
                        for (i in (0 until rawItems.length())) {
                            contents.add(ComponentKey(context, rawItems.getString(i)))
                        }
                    }
                    Tab(title, hideFromAllApps, contents)
                }
    }

    fun getTabs(): List<Tab> {
        return tabs
    }

    fun setTabs(tabs: List<Tab>) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
    }

    fun saveToJson() {
        val arr = JSONArray()
        tabs.forEach {tab ->
            val items = JSONArray()
            tab.contents.forEach { items.put(it.toString()) }

            val obj = JSONObject()
            obj.put(KEY_TITLE, tab.title)
            obj.put(KEY_HIDE_FROM_ALL_APPS, tab.hideFromAllApps)
            obj.put(KEY_ITEMS, items)

            arr.put(obj)
        }
        tabsDataJson = arr.toString()
    }

    companion object {

        const val KEY_TITLE = "title"
        const val KEY_ITEMS = "items"
        const val KEY_HIDE_FROM_ALL_APPS = "hideFromAllApps"
    }

    class Tab(var title: String,
              var hideFromAllApps: Boolean = true,
              val contents: MutableSet<ComponentKey> = mutableSetOf())
}
