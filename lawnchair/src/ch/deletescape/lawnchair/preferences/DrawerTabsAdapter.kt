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
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.DrawerTabs
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import ch.deletescape.lawnchair.settings.ui.SettingsBottomSheetDialog
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R
import com.android.launcher3.compat.UserManagerCompat

class DrawerTabsAdapter(private val context: Context) : RecyclerView.Adapter<DrawerTabsAdapter.Holder>() {

    private val drawerTabs = context.lawnchairPrefs.drawerTabs
    private val tabs = ArrayList<DrawerTabs.Tab>()
    private val accent = ColorEngine.getInstance(context).accent

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

    fun addTab(config: DrawerTabEditBottomSheet.TabConfig) {
        val tab = DrawerTabs.CustomTab(config.title, config.hideFromMain, config.contents, config.colorResolver)
        tabs.add(tab)
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

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val summary: TextView = itemView.findViewById(R.id.summary)
        private val edit: ImageView = itemView.findViewById(R.id.edit)
        private val delete: View = itemView.findViewById(R.id.delete)

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
            title.text = if (info is DrawerTabs.PersonalTab)
                info.loadTitle(context, hasWorkApps) else info.title
            summary.isVisible = info is DrawerTabs.CustomTab
            edit.isVisible = info is DrawerTabs.CustomTab
            edit.tintDrawable(accent)
            delete.isVisible = info is DrawerTabs.CustomTab
            if (info is DrawerTabs.CustomTab) {
                val size = info.contents.size
                summary.text = context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
            }
        }

        private fun startEdit() {
            val tab = tabs[adapterPosition] as? DrawerTabs.CustomTab ?: return
            DrawerTabEditBottomSheet.edit(context, tab)
        }

        private fun delete() {
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

    companion object {
        const val ITEM_RENAME = 0
        const val ITEM_DELETE = 1
    }
}
