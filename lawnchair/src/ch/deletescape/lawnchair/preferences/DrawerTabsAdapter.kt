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
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.TextUtils
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.groups.AppGroups
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.groups.DrawerTabs
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R
import com.android.launcher3.compat.UserManagerCompat

class DrawerTabsAdapter(private val context: Context) : RecyclerView.Adapter<DrawerTabsAdapter.Holder>() {

    private val drawerTabs = context.lawnchairPrefs.drawerTabs
    private val tabs = ArrayList<DrawerTabs.Tab>()
    private val accent = ColorEngine.getInstance(context).accent

    val itemTouchHelper = ItemTouchHelper(TouchHelperCallback())

    private var saved = true

    private val hasWorkApps = context.lawnchairPrefs.separateWorkApps
            && UserManagerCompat.getInstance(context).userProfiles.size > 1

    override fun getItemCount() = tabs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.tab_item, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(tabs[position])
    }

    fun addTab(config: AppGroups.Group.CustomizationMap) {
        val tab = DrawerTabs.CustomTab(context)
        tab.customizations.applyFrom(config)
        tabs.add(tab)
        notifyItemInserted(tabs.size - 1)
        saved = false
    }

    fun reloadTabs() {
        tabs.clear()
        if (hasWorkApps) {
            tabs.addAll(drawerTabs.getGroups().filter { it !is DrawerTabs.AllAppsTab })
        } else {
            tabs.addAll(drawerTabs.getGroups().filter { it !is DrawerTabs.PersonalTab && it !is DrawerTabs.WorkTab })
        }
        notifyDataSetChanged()
    }

    fun saveChanges() {
        if (saved) return
        drawerTabs.setGroups(tabs)
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

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val summary: TextView = itemView.findViewById(R.id.summary)
        private val edit: ImageView = itemView.findViewById(R.id.edit)
        private val delete: ImageView = itemView.findViewById(R.id.delete)
        private var deleted = false

        init {
            itemView.findViewById<View>(R.id.drag_handle).setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                }
                false
            }
            itemView.setOnClickListener { startEdit() }
            edit.setOnClickListener { startEdit()}
            delete.setOnClickListener { delete() }
        }

        fun bind(info: DrawerTabs.Tab) {
            title.text = info.getTitle()
            edit.tintDrawable(accent)
            delete.isVisible = info is DrawerTabs.CustomTab
            delete.tintDrawable(accent)
            summary.text = info.getSummary(context)
            summary.isVisible = !TextUtils.isEmpty(summary.text)
            deleted = false
        }

        private fun startEdit() {
            if (deleted) return
            val tab = tabs[adapterPosition]
            DrawerTabEditBottomSheet.edit(context, tab, ::reloadTabs)
        }

        private fun delete() {
            if (deleted) return
            deleted = true
            tabs.removeAt(adapterPosition)
            notifyItemRemoved(adapterPosition)
            saved = false
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
}
