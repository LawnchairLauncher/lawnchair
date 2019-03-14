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

import android.content.Context
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DrawerTabs(prefs: LawnchairPreferences) {

    private var tabsDataJson by prefs.StringPref("pref_drawerTabs", "[]", prefs.recreate)
    private val tabs = ArrayList<Tab>()

    private val context = prefs.context

    init {
        loadTabs()
    }

    private fun loadTabsArray(): JSONArray {
        try {
            return JSONArray(tabsDataJson)
        } catch (ignored: JSONException) {
        }

        try {
            val obj = JSONObject(tabsDataJson)
            val version = if (obj.has(KEY_VERSION)) obj.getInt(KEY_VERSION) else 0
            if (version > currentVersion) return JSONArray()
            return obj.getJSONArray(KEY_TABS)
        } catch (ignored: JSONException) {
        }

        return JSONArray()
    }

    private fun loadTabs() {
        tabs.clear()
        var personalAdded = false
        var workAdded = false
        val arr = loadTabsArray()
        (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .mapNotNullTo(tabs) { tab ->
                    val type = if (tab.has(KEY_TYPE)) tab.getInt(KEY_TYPE) else TYPE_CUSTOM
                    when (type) {
                        TYPE_CUSTOM -> {
                            val title = if (tab.has(KEY_TITLE)) tab.getString(KEY_TITLE) else ""
                            val hideFromAllApps = if (tab.has(KEY_HIDE_FROM_ALL_APPS))
                                tab.getBoolean(KEY_HIDE_FROM_ALL_APPS) else true
                            val contents = mutableSetOf<ComponentKey>()
                            if (tab.has(KEY_ITEMS)) {
                                val rawItems = tab.getJSONArray(KEY_ITEMS)
                                for (i in (0 until rawItems.length())) {
                                    contents.add(ComponentKey(context, rawItems.getString(i)))
                                }
                            }
                            CustomTab(title, hideFromAllApps, contents)
                        }
                        TYPE_PERSONAL -> {
                            personalAdded = true
                            PersonalTab(context)
                        }
                        TYPE_WORK -> {
                            workAdded = true
                            WorkTab(context)
                        }
                        else -> null
                    }
                }
        if (!personalAdded) {
            tabs.add(0, PersonalTab(context))
        }
        if (!workAdded) {
            tabs.add(WorkTab(context))
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
        var workAdded = false
        tabs.forEach {tab ->
            arr.put(JSONObject().also { tab.saveToJson(it) })
            if (tab is WorkTab) {
                workAdded = true
            }
        }
        if (!workAdded) {
            tabs.add(WorkTab(context))
        }

        val obj = JSONObject()
        obj.put(KEY_VERSION, currentVersion)
        obj.put(KEY_TABS, arr)
        tabsDataJson = obj.toString()
    }

    companion object {

        const val currentVersion = 1

        const val KEY_VERSION = "version"
        const val KEY_TABS = "tabs"

        const val KEY_TYPE = "type"
        const val KEY_TITLE = "title"
        const val KEY_ITEMS = "items"
        const val KEY_HIDE_FROM_ALL_APPS = "hideFromAllApps"

        const val TYPE_PERSONAL = 0
        const val TYPE_WORK = 1
        const val TYPE_CUSTOM = 2
    }

    open class Tab(var title: String, private val type: Int) {

        open fun saveToJson(obj: JSONObject) {
            obj.put(KEY_TYPE, type)
        }
    }

    class CustomTab(title: String, var hideFromAllApps: Boolean = true,
                    val contents: MutableSet<ComponentKey> = mutableSetOf()) : Tab(title, TYPE_CUSTOM) {

        override fun saveToJson(obj: JSONObject) {
            val items = JSONArray()
            contents.forEach { items.put(it.toString()) }

            obj.put(KEY_TITLE, title)
            obj.put(KEY_HIDE_FROM_ALL_APPS, hideFromAllApps)
            obj.put(KEY_ITEMS, items)
        }
    }

    class PersonalTab(context: Context) : Tab(context.getString(R.string.all_apps_personal_tab), TYPE_PERSONAL) {

        fun loadTitle(context: Context, hasWorkApps: Boolean): String {
            return context.getString(if (hasWorkApps) R.string.all_apps_personal_tab else R.string.all_apps_label)
        }
    }

    class WorkTab(context: Context) : Tab(context.getString(R.string.all_apps_work_tab), TYPE_WORK)
}
