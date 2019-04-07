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

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.res.ColorStateList
import android.view.View
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.comparing
import ch.deletescape.lawnchair.then
import com.android.launcher3.AppFilter
import com.android.launcher3.util.ComponentKey
import java.util.Comparator
import kotlin.reflect.KMutableProperty0

abstract class SelectableAppsAdapter(context: Context, private val callback: Callback? = null, filter: AppFilter? = null)
    : AppsAdapter(context, null, filter) {

    private val selections = HashSet<ComponentKey>()
    private val accentTintList = ColorStateList.valueOf(ColorEngine.getInstance(context).accent)

    override val comparator = comparing<App, Int> { if (isSelected(it.key)) 0 else 1 }
            .then { it.info.label.toString().toLowerCase() }

    init {
        postLoadApps()
        callback?.onSelectionsChanged(0)
    }

    override fun onAppsListLoaded() {
        val tmp = HashSet(selections)
        selections.clear()
        apps.forEach {
            if (it.key in tmp) {
                selections.add(it.key)
            }
        }
        super.onAppsListLoaded()
        callback?.onSelectionsChanged(selections.size)
    }

    override fun onBindApp(app: AppsAdapter.App, holder: AppHolder) {
        super.onBindApp(app, holder)
        holder.checkBox.apply {
            visibility = View.VISIBLE
            isChecked = isSelected(app.key)
            buttonTintList = accentTintList
        }
    }

    override fun onClickApp(position: Int, holder: AppHolder) {
        super.onClickApp(position, holder)
        toggleSelection(position)
        holder.checkBox.isChecked = isSelected(apps[position].key)
    }

    private fun isSelected(component: ComponentKey) = selections.contains(component)

    private fun toggleSelection(position: Int) {
        val app = apps[position]
        val componentKey = app.key
        if (selections.contains(componentKey)) {
            selections.remove(componentKey)
        } else {
            selections.add(componentKey)
        }
        setSelections(selections)
        callback?.onSelectionsChanged(selections.size)
    }

    fun clearSelection() {
        selections.clear()
        setSelections(selections)
        callback?.onSelectionsChanged(0)
        notifyDataSetChanged()
    }

    override fun loadAppsList() {
        selections.addAll(getInitialSelections())
        super.loadAppsList()
    }

    abstract fun getInitialSelections(): Set<ComponentKey>

    abstract fun setSelections(selections: Set<ComponentKey>)

    interface Callback {

        fun onSelectionsChanged(newSize: Int)
    }

    companion object {

        fun ofProperty(context: Context, property: KMutableProperty0<Set<String>>,
                       callback: Callback? = null, filter: AppFilter? = null)
                = object : SelectableAppsAdapter(context, callback, filter) {

            override fun getInitialSelections() = HashSet(property.get().map { ComponentKey(context, it) })

            override fun setSelections(selections: Set<ComponentKey>) {
                property.set(HashSet(selections.map { it.toString() }))
            }
        }
    }
}
