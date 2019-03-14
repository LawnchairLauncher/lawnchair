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
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import android.widget.TextView
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.DrawerTabs
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.R
import com.android.launcher3.compat.UserManagerCompat

class DrawerTabsAdapter(private val context: Context) : RecyclerView.Adapter<DrawerTabsAdapter.Holder>() {

    private val drawerTabs = context.lawnchairPrefs.drawerTabs
    private val tabs = ArrayList<DrawerTabs.Tab>()

    val itemTouchHelper = ItemTouchHelper(TouchHelperCallback())

    private var saved = true

    private val hasWorkApps = UserManagerCompat.getInstance(context).userProfiles.size > 1

    override fun getItemCount() = tabs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.tab_item, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(tabs[position])
    }

    fun addTab(title: String) {
        tabs.add(DrawerTabs.CustomTab(title))
        notifyItemInserted(tabs.size - 1)
        saved = false
    }

    fun reloadTabs() {
        tabs.clear()
        if (hasWorkApps) {
            tabs.addAll(drawerTabs.getTabs())
        } else {
            tabs.addAll(drawerTabs.getTabs().filter { it !is DrawerTabs.WorkTab })
        }
        notifyDataSetChanged()
    }

    fun saveChanges() {
        if (saved) return
        drawerTabs.setTabs(tabs)
        drawerTabs.saveToJson()
        saved = true
    }

    private fun move(from: Int, to: Int): Boolean {
        if (to == from) return true
        tabs.add(to, tabs.removeAt(from))
        notifyItemMoved(from, to)
        saved = false
        return true
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {

        private val title: TextView = itemView.findViewById(android.R.id.title)
        private val dragHandle: View = itemView.findViewById(R.id.drag_handle)

        init {
            itemView.setOnCreateContextMenuListener(this)
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                }
                false
            }
        }

        fun bind(info: DrawerTabs.Tab) {
            title.text = if (info is DrawerTabs.PersonalTab)
                info.loadTitle(context, hasWorkApps) else info.title
            if (info is DrawerTabs.CustomTab) {
                itemView.setOnClickListener {
                    SettingsActivity.startFragment(context, DrawerTabEditFragment::class.java.name,
                            Bundle().apply {
                                putInt(DrawerTabEditFragment.EXTRA_INDEX, adapterPosition)
                            })
                }
            } else {
                itemView.setOnClickListener(null)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val position = adapterPosition
            when (item.itemId) {
                ITEM_RENAME -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.tab_title_input, null)
                    val title = view.findViewById(android.R.id.edit) as TextView
                    title.text = tabs[position].title
                    AlertDialog.Builder(context)
                            .setTitle(R.string.rename_tab)
                            .setView(view)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                tabs[position].title = title.text.toString()
                                notifyItemChanged(position)
                            }
                            .show()
                }
                ITEM_DELETE -> {
                    tabs.removeAt(position)
                    notifyItemRemoved(position)
                }
            }
            saved = false
            return true
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            if (tabs[adapterPosition] !is DrawerTabs.CustomTab) return
            menu.add(0, ITEM_RENAME, 0, R.string.rename_tab)
            menu.add(0, ITEM_DELETE, 0, R.string.delete_tab)
            for (i in (0 until menu.size())) {
                menu.getItem(i).setOnMenuItemClickListener(this)
            }
        }
    }

    inner class TouchHelperCallback : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return ItemTouchHelper.Callback.makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return move(viewHolder.adapterPosition, target.adapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }
    }

    companion object {
        const val ITEM_RENAME = 0
        const val ITEM_DELETE = 1
    }
}
