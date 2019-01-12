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

import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.forEachChildIndexed
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsPagedView
import com.android.launcher3.allapps.AllAppsStore

class AllAppsTabsController(val tabs: AllAppsTabs, container: AllAppsContainerView,
        private val adapterHolders: Array<AllAppsContainerView.AdapterHolder>) {

    val tabsCount get() = tabs.count
    val shouldShowTabs get() = tabsCount > 1

    init {
        for (i in (0 until adapterHolders.size)) {
            adapterHolders[i] = container.createHolder(false)
        }
    }

    fun registerIconContainers(allAppsStore: AllAppsStore) {
        adapterHolders.forEach { allAppsStore.registerIconContainer(it.recyclerView) }
    }

    fun unregisterIconContainers(allAppsStore: AllAppsStore) {
        adapterHolders.forEach { allAppsStore.unregisterIconContainer(it.recyclerView) }
    }

    fun setup(pagedView: AllAppsPagedView) {
        tabs.forEachIndexed { index, tab ->
            adapterHolders[index].setIsWork(tab.isWork)
            adapterHolders[index].setup(pagedView.getChildAt(index), tab.matcher)
        }
    }

    fun setup(view: View) {
        adapterHolders.forEach { it.recyclerView = null }
        adapterHolders[0].setup(view, null)
    }

    fun bindButtons(buttonsContainer: ViewGroup, pagedView: AllAppsPagedView) {
        buttonsContainer.forEachChildIndexed { view, i ->
            view.setOnClickListener { pagedView.snapToPage(i) }
        }
    }
}
