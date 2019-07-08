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
import android.content.Intent
import android.os.Handler
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.getColorEngineAccent
import ch.deletescape.lawnchair.iconpack.IconPackList
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.isVisible
import com.android.launcher3.R
import kotlin.collections.ArrayList

class IconPackAdapter(context: Context) : RecyclerView.Adapter<IconPackAdapter.Holder>() {

    private val manager = IconPackManager.getInstance(context)
    private val allPacks = ArrayList<IconPackItem>()
    private val handler = Handler()

    private var dividerIndex = 0

    private val adapterItems = ArrayList<Item>()
    private val currentSpecs = ArrayList<String>()
    private val otherItems = ArrayList<IconPackItem>()
    private val divider = DividerItem(IconPackList.DefaultPackInfo(context))
    private var isDragging = false

    var itemTouchHelper: ItemTouchHelper? = null

    init {
        allPacks.addAll(manager.packList.getAvailablePacks().map { IconPackItem(it) })
        currentSpecs.addAll(manager.packList.appliedPacks.map { it.packPackageName })

        fillItems()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return when (viewType) {
            TYPE_HEADER -> createHolder(parent, R.layout.icon_pack_text_item, ::HeaderHolder)
            TYPE_PACK -> createHolder(parent, R.layout.icon_pack_dialog_item, ::IconPackHolder)
            TYPE_DIVIDER -> createHolder(parent, R.layout.icon_pack_divider_item, ::DividerHolder)
            TYPE_DOWNLOAD -> createHolder(parent, R.layout.icon_pack_dialog_item, ::DownloadHolder)
            else -> throw IllegalArgumentException("type must be either TYPE_TEXT, " +
                    "TYPE_PACK, TYPE_DIVIDER or TYPE_DOWNLOAD")
        }
    }

    override fun getItemCount() = adapterItems.count()

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(adapterItems[position])
    }

    override fun getItemViewType(position: Int): Int {
        return adapterItems[position].type
    }

    fun saveSpecs(): ArrayList<String> {
        val newSpecs = ArrayList<String>()
        val iterator = adapterItems.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item is IconPackItem) newSpecs.add(item.info.packageName)
            if (item is DividerItem) break
        }
        return newSpecs
    }

    private fun fillItems(){
        otherItems.clear()
        otherItems.addAll(allPacks)

        adapterItems.clear()
        adapterItems.add(HeaderItem())
        currentSpecs.forEach {
            val item = getAndRemoveOther(it)
            if (item != null) {
                adapterItems.add(item)
            }
        }
        dividerIndex = adapterItems.count()
        adapterItems.add(divider)
        adapterItems.addAll(otherItems)
        adapterItems.add(DownloadItem())
    }

    private fun getAndRemoveOther(s: String): IconPackItem? {
        val iterator = otherItems.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.info.packageName == s) {
                iterator.remove()
                return item
            }
        }
        return null
    }

    private fun move(from: Int, to: Int): Boolean {
        if (to == from) return true
        move(from, to, adapterItems)
        dividerIndex = adapterItems.indexOf(divider)
        return true
    }

    private fun <T> move(from: Int, to: Int, list: MutableList<T>) {
        list.add(to, list.removeAt(from))
        notifyItemMoved(from, to)
    }

    private inline fun createHolder(parent: ViewGroup, resource: Int, creator: (View) -> Holder): Holder {
        return creator(LayoutInflater.from(parent.context).inflate(resource, parent, false))
    }

    abstract class Item {

        abstract val isStatic: Boolean
        abstract val type: Int
    }

    class HeaderItem : Item() {

        override val isStatic = true
        override val type = TYPE_HEADER
    }

    open class IconPackItem(val info: IconPackList.PackInfo) : Item() {

        override val isStatic = false
        override val type = TYPE_PACK
    }

    class DividerItem(info: IconPackList.PackInfo): IconPackItem(info) {

        override val isStatic = true
        override val type = TYPE_DIVIDER
    }

    class DownloadItem : Item() {

        override val isStatic = true
        override val type = TYPE_DOWNLOAD
    }

    abstract class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(item: Item) {

        }
    }

    class HeaderHolder(itemView: View) : Holder(itemView) {

        init {
            itemView.findViewById<TextView>(android.R.id.text1).apply {
                setText(R.string.enabled_icon_packs)
                setTextColor(context.getColorEngineAccent())
            }
        }
    }

    open inner class IconPackHolder(itemView: View) : Holder(itemView), View.OnClickListener, View.OnTouchListener {

        val icon: ImageView = itemView.findViewById(android.R.id.icon)
        val title: TextView = itemView.findViewById(android.R.id.title)
        val summary: TextView = itemView.findViewById(android.R.id.summary)
        private val dragHandle: View = itemView.findViewById(R.id.drag_handle)
        private val packItem get() = adapterItems[adapterPosition] as? IconPackItem
                ?: throw IllegalArgumentException("item must be IconPackItem")

        init {
            itemView.setOnClickListener(this)
            dragHandle.setOnTouchListener(this)
        }

        override fun bind(item: Item) {
            val packItem = item as? IconPackItem
                    ?: throw IllegalArgumentException("item must be IconPackItem")
            icon.setImageDrawable(packItem.info.displayIcon)
            title.text = packItem.info.displayName
            itemView.isClickable = !packItem.isStatic
            dragHandle.isVisible = !packItem.isStatic
        }

        override fun onClick(v: View) {
            val item = packItem
            if (adapterPosition > dividerIndex) {
                adapterItems.removeAt(adapterPosition)
                adapterItems.add(1, item)
                notifyItemMoved(adapterPosition, 1)
                dividerIndex++
            } else {
                adapterItems.removeAt(adapterPosition)
                adapterItems.add(dividerIndex, item)
                notifyItemMoved(adapterPosition, dividerIndex)
                dividerIndex--
            }
            notifyItemChanged(dividerIndex)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (v == dragHandle && event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(this)
            }
            return false
        }
    }

    inner class DividerHolder(itemView: View) : IconPackHolder(itemView) {

        val text: TextView = itemView.findViewById(android.R.id.text1)

        init {
            text.setTextColor(text.context.getColorEngineAccent())
        }

        override fun bind(item: Item) {
            super.bind(item)
            if (isDragging) {
                text.setText(R.string.drag_to_disable_packs)
            } else {
                text.setText(R.string.drag_to_enable_packs)
            }
            text.isVisible = isDragging || dividerIndex != adapterItems.size - 2
        }
    }

    inner class DownloadHolder(itemView: View) : IconPackHolder(itemView) {

        init {
            val context = itemView.context
            icon.setImageDrawable(context.getDrawable(R.drawable.ic_add)?.apply {
                setTint(ColorEngine.getInstance(context).accent)
            })
            title.setText(R.string.get_more_icon_packs)
        }

        override fun bind(item: Item) {

        }

        override fun onClick(v: View) {
            val intent = Intent.parseUri(v.context.getString(R.string.market_search_intent), 0)
            intent.data = intent.data!!.buildUpon().appendQueryParameter("q", v.context.getString(R.string.icon_pack)).build()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.context.startActivity(intent)
        }
    }

    inner class TouchHelperCallback : ItemTouchHelper.Callback() {

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            handler.post { notifyItemChanged(dividerIndex) }
        }

        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return target.adapterPosition in 1..dividerIndex
        }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val item = adapterItems[viewHolder.adapterPosition]
            val dragFlags = if (item.isStatic) 0 else ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return ItemTouchHelper.Callback.makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return move(viewHolder.adapterPosition, target.adapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }
    }

    companion object {

        const val TYPE_HEADER = 0
        const val TYPE_PACK = 1
        const val TYPE_DIVIDER = 2
        const val TYPE_DOWNLOAD = 3
    }
}
