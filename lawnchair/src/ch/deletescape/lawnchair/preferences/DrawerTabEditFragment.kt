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

import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import ch.deletescape.lawnchair.LawnchairAppFilter
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.util.ComponentKey

class DrawerTabEditFragment : RecyclerViewFragment(), SelectableAppsAdapter.Callback {

    private var tabContents: Set<String> = emptySet()
    private val tabIndex by lazy { arguments!!.getInt(EXTRA_INDEX) }
    private val drawerTabs by lazy { activity!!.lawnchairPrefs.drawerTabs }
    private val tab by lazy { drawerTabs.getTabs()[tabIndex] }

    override fun onRecyclerViewCreated(recyclerView: RecyclerView) {
        tabContents = loadContents()

        activity!!.title = tab.title

        val context = recyclerView.context
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = SelectableAppsAdapter.ofProperty(activity!!,
                ::tabContents, this, LawnchairAppFilter(context))
    }

    private fun loadContents(): Set<String> {
        return tab.contents.map {
            ComponentKey(it.targetComponent, it.user).toString()
        }.toSet()
    }

    override fun onPause() {
        super.onPause()

        tab.contents = ArrayList(tabContents.map {
            val key = ComponentKey(activity!!, it)
            ShortcutInfo("", Intent.makeMainActivity(key.componentName), key.user)
        })
        drawerTabs.saveToJson()
    }

    override fun onSelectionsChanged(newSize: Int) {

    }

    companion object {
        const val EXTRA_INDEX = "index"
    }
}
