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

package ch.deletescape.lawnchair.groups.ui

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.TextUtils
import android.util.LayoutDirection
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.createDisabledColor
import ch.deletescape.lawnchair.groups.AppGroups
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.preferences.DrawerTabEditBottomSheet
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R

abstract class AppGroupsAdapter<VH : AppGroupsAdapter<VH, T>.GroupHolder, T : AppGroups.Group>(val context: Context)
    : RecyclerView.Adapter<AppGroupsAdapter.Holder>() {

    private var saved = true

    protected val manager = context.lawnchairPrefs.appGroupsManager
    protected val items = ArrayList<Item>()
    protected val accent = ColorEngine.getInstance(context).accent
    protected abstract val groupsModel: AppGroups<T>
    protected abstract val headerText: Int

    val itemTouchHelper = ItemTouchHelper(TouchHelperCallback())

    private var headerItemCount = 0

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is HeaderItem -> TYPE_HEADER
            is AddItem -> TYPE_ADD
            is GroupItem -> TYPE_GROUP
            else -> throw IllegalStateException("Unknown item class $item")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(LayoutInflater.from(parent.context).inflate(R.layout.app_groups_adapter_header, parent, false))
            TYPE_ADD -> AddHolder(LayoutInflater.from(parent.context).inflate(R.layout.app_groups_adapter_add, parent, false))
            TYPE_GROUP -> createGroupHolder(parent)
            else -> throw IllegalStateException("Unknown view type $viewType")
        }
    }

    abstract fun createGroupHolder(parent: ViewGroup): VH

    override fun onBindViewHolder(holder: Holder, position: Int) {
        when (holder) {
            is AppGroupsAdapter<*, *>.GroupHolder -> holder.bind((items[position] as GroupItem).group)
        }
    }

    abstract fun filterGroups(): Collection<T>

    open fun loadAppGroups() {
        items.clear()
        headerItemCount = 0
        createHeaderItem()?.let {
            items.add(it)
            headerItemCount++
        }
        createAddItem()?.let {
            items.add(it)
            headerItemCount++
        }
        items.addAll(filterGroups().map { GroupItem(it) })
        notifyDataSetChanged()
    }

    open fun createHeaderItem(): Item? {
        return HeaderItem()
    }

    open fun createAddItem(): Item? {
        return AddItem()
    }

    open fun addGroup(group: T) {
        items.add(GroupItem(group))
        notifyItemInserted(items.size - 1)
        saved = false
    }

    open fun saveChanges() {
        if (saved) return
        groupsModel.setGroups(items.mapNotNull { (it as? GroupItem)?.group })
        groupsModel.saveToJson()
        saved = true
    }

    protected open fun move(from: Int, to: Int): Boolean {
        if (to < headerItemCount) return false
        if (to == from) return true
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        saved = false
        return true
    }

    fun showAddDialog() {
        createGroup { group, animate ->
            DrawerTabEditBottomSheet.newGroup(context, group, animate) {
                group.customizations.applyFrom(it)
                addGroup(group)
                saveChanges()
            }
        }
    }

    fun showEditDialog(group: T) {
        DrawerTabEditBottomSheet.edit(context, group) {
            saved = false
            saveChanges()
            loadAppGroups()
        }
    }

    abstract fun createGroup(callback: (group: T, animate: Boolean) -> Unit)

    open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class HeaderHolder(itemView: View) : Holder(itemView) {

        init {
            val context = itemView.context
            val title = itemView.findViewById<View>(R.id.categoryHeader).findViewById<TextView>(android.R.id.title)
            title.setText(headerText)
            title.setTextColor(context.createDisabledColor(accent))

            val tipIcon = itemView.findViewById<View>(R.id.tipRow).findViewById<ImageView>(android.R.id.icon)
            tipIcon.tintDrawable(accent)
        }
    }

    inner class AddHolder(itemView: View) : Holder(itemView), View.OnClickListener {

        init {
            val title = itemView.findViewById<TextView>(android.R.id.title)
            title.setTextColor(accent)

            val icon = itemView.findViewById<ImageView>(android.R.id.icon)
            icon.tintDrawable(accent)

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            showAddDialog()
        }
    }

    open inner class GroupHolder(itemView: View) : Holder(itemView) {

        protected val title: TextView = itemView.findViewById(R.id.title)
        protected val summary: TextView = itemView.findViewById(R.id.summary)
        protected val delete: ImageView = itemView.findViewById(R.id.delete)
        private var deleted = false

        init {
            itemView.findViewById<View>(R.id.drag_handle).setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                }
                false
            }
            itemView.setOnClickListener { startEdit() }
            delete.setOnClickListener { delete() }
            delete.tintDrawable(accent)
        }

        open fun bind(info: AppGroups.Group) {
            title.text = info.getTitle()
            summary.text = formatSummary(info.getSummary(context))
            summary.isVisible = !TextUtils.isEmpty(summary.text)
            deleted = false
        }

        protected open fun formatSummary(summary: String?): String? {
            if (summary.isNullOrEmpty()) return null
            return if (itemView.layoutDirection == LayoutDirection.LTR) {
                "— $summary"
            } else {
                "$summary —"
            }
        }

        private fun startEdit() {
            if (deleted) return
            val group = (items[adapterPosition] as GroupItem).group
            showEditDialog(group)
        }

        private fun delete() {
            if (deleted) return
            deleted = true
            items.removeAt(adapterPosition)
            notifyItemRemoved(adapterPosition)
            saved = false
        }
    }

    open inner class Item
    inner class HeaderItem : Item()
    inner class AddItem : Item()
    inner class GroupItem(val group: T) : Item()

    inner class TouchHelperCallback : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            if (viewHolder !is AppGroupsAdapter<*, *>.GroupHolder) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return move(viewHolder.adapterPosition, target.adapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }
    }

    companion object {

        const val TYPE_HEADER = 0
        const val TYPE_ADD = 1
        const val TYPE_GROUP = 2
    }
}
