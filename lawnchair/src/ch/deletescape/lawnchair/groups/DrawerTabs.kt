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
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.LawnchairPreferencesChangeCallback
import ch.deletescape.lawnchair.groups.FlowerpotTabs.FlowerpotTab
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.preferences.SelectableAppsActivity
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

abstract class DrawerTabs(manager: AppGroupsManager, type: AppGroupsManager.CategorizationType)
    : AppGroups<DrawerTabs.Tab>(manager, type) {

    override fun getDefaultCreators(): List<GroupCreator<Tab>> {
        return listOf(::createAllAppsTab, ::createPersonalTab, ::createWorkTab)
    }

    override fun getGroupCreator(type: Int): GroupCreator<Tab> {
        return when (type) {
            TYPE_CUSTOM, TYPE_UNDEFINED -> ::createCustomTab
            TYPE_PERSONAL -> ::createPersonalTab
            TYPE_WORK -> ::createWorkTab
            TYPE_ALL_APPS -> ::createAllAppsTab
            FlowerpotTabs.TYPE_FLOWERPOT -> ::FlowerpotTab
            else -> ::createNull
        }
    }

    private fun createPersonalTab(context: Context) = PersonalTab(context)

    private fun createWorkTab(context: Context) = WorkTab(context)

    private fun createAllAppsTab(context: Context) = AllAppsTab(context)

    private fun createCustomTab(context: Context) = CustomTab(context)

    override fun onGroupsChanged(changeCallback: LawnchairPreferencesChangeCallback) {
        changeCallback.launcher.allAppsController.appsView.reloadTabs()
    }

    abstract class Tab(context: Context, type: Int, titleRes: Int) : Group(type, context, titleRes) {

        val colorResolver = ColorRow(KEY_COLOR, AppGroupsUtils.getInstance(context).defaultColorResolver)

        init {
            addCustomization(colorResolver)
        }
    }

    class CustomTab(context: Context) : Tab(context, TYPE_CUSTOM, R.string.default_tab_name) {

        val hideFromAllApps = SwitchRow(R.drawable.tab_hide_from_main, R.string.tab_hide_from_main,
                KEY_HIDE_FROM_ALL_APPS, true)
        val contents = AppsRow(KEY_ITEMS, mutableSetOf())

        init {
            addCustomization(hideFromAllApps)
            addCustomization(contents)

            customizations.setOrder(KEY_TITLE, KEY_HIDE_FROM_ALL_APPS, KEY_COLOR, KEY_ITEMS)
        }

        override fun getSummary(context: Context): String? {
            val size = getFilter(context).size
            return context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
        }

        fun getFilter(context: Context): Filter<*> = CustomFilter(context, contents.value()) // IconPackFilter(context)
    }

    open class PredefinedTab(context: Context, type: Int, titleRes: Int,
                             private val filterIsWork: Boolean?) : Tab(context, type, titleRes) {

        init {
            addCustomization(HiddenAppsRow(filterIsWork))
            customizations.setOrder(KEY_TITLE, KEY_COLOR, KEY_HIDDEN)
        }

        override fun getSummary(context: Context): String? {
            val hidden = context.lawnchairPrefs.hiddenAppSet
                    .map { ComponentKey(context, it) }
                    .filter(getWorkFilter(filterIsWork))
            val size = hidden.size
            if (size == 0) {
                return null
            }
            return context.resources.getQuantityString(R.plurals.hidden_apps_count, size, size)
        }
    }

    class AllAppsTab(context: Context) : PredefinedTab(context, TYPE_ALL_APPS, R.string.apps_label, null)

    class PersonalTab(context: Context) : PredefinedTab(context, TYPE_PERSONAL, R.string.all_apps_personal_tab, false)

    class WorkTab(context: Context) : PredefinedTab(context, TYPE_WORK, R.string.all_apps_work_tab, true)

    class HiddenAppsRow(private val filterIsWork: Boolean? = null) :
            Group.Customization<Collection<ComponentKey>, Boolean>(KEY_HIDDEN, emptySet()) {

        private val predicate get() = getWorkFilter(filterIsWork)

        override fun createRow(context: Context, parent: ViewGroup, accent: Int): View? {
            val view = LayoutInflater.from(context).inflate(R.layout.drawer_tab_hidden_apps_row, parent, false)

            view.findViewById<ImageView>(R.id.manage_apps_icon).tintDrawable(accent)
            updateCount(view)

            view.setOnClickListener {
                SelectableAppsActivity.start(context, filteredValue(context), { newSelections ->
                    if (newSelections != null) {
                        value = HashSet(newSelections)
                        updateCount(view)
                    }
                }, filterIsWork)
            }

            return view
        }

        override fun loadFromJson(context: Context, obj: Boolean?) {

        }

        override fun saveToJson(context: Context): Boolean? {
            val value = value ?: return null
            setHiddenApps(context, value)
            this.value = null
            return null
        }

        private fun updateCount(view: View) {
            val count = (value ?: filteredValue(view.context)).size
            view.findViewById<TextView>(R.id.apps_count).text =
                    view.resources.getQuantityString(R.plurals.hidden_apps_count, count, count)
        }

        private fun filteredValue(context: Context): Collection<ComponentKey> {
            return context.lawnchairPrefs.hiddenAppSet
                    .map { ComponentKey(context, it) }
                    .filter(predicate)
        }

        private fun setHiddenApps(context: Context, hidden: Collection<ComponentKey>) {
            val prefs = context.lawnchairPrefs
            val hiddenSet = ArrayList(prefs.hiddenAppSet
                    .map { ComponentKey(context, it) }
                    .filter { !predicate(it) })
            hiddenSet.addAll(hidden)
            prefs.hiddenAppSet = hiddenSet.map(ComponentKey::toString).toSet()
        }

        override fun clone(): Group.Customization<Collection<ComponentKey>, Boolean> {
            return HiddenAppsRow(filterIsWork).also { it.value = value }
        }
    }

    companion object {

        const val TYPE_PERSONAL = 0
        const val TYPE_WORK = 1
        const val TYPE_CUSTOM = 2
        const val TYPE_ALL_APPS = 3

        const val KEY_ITEMS = "items"
        const val KEY_HIDDEN = "hidden"

        private val noFilter = { _: ComponentKey -> true }
        private val personalFilter = { key: ComponentKey ->
            key.user == null || key.user == Process.myUserHandle()
        }
        private val workFilter = { key: ComponentKey ->
            key.user != null && key.user != Process.myUserHandle()
        }

        fun getWorkFilter(isWork: Boolean?): (ComponentKey) -> Boolean {
            return when (isWork) {
                null -> noFilter
                false -> personalFilter
                true -> workFilter
            }
        }
    }
}
