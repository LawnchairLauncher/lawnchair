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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.annotation.Keep
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import android.widget.TextView
import com.android.launcher3.R

@Keep
class DrawerTabsFragment : RecyclerViewFragment() {

    private var adapter: DrawerTabsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onRecyclerViewCreated(recyclerView: RecyclerView) {
        val context = recyclerView.context
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = DrawerTabsAdapter(context).apply {
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        adapter!!.reloadTabs()
    }

    override fun onPause() {
        super.onPause()

        adapter?.saveChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_drawer_folders, menu)
    }

    @SuppressLint("InflateParams")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                val context = activity as Context
                val view = LayoutInflater.from(context).inflate(R.layout.tab_title_input, null)
                val title = view.findViewById(android.R.id.edit) as TextView
                AlertDialog.Builder(context)
                        .setTitle(R.string.add_new_tab)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            adapter!!.addTab(title.text.toString())
                        }
                        .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
