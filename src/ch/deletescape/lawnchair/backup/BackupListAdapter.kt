package ch.deletescape.lawnchair.backup

import android.content.Context
import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.launcher3.R
import com.github.florent37.fiftyshadesof.FiftyShadesOf

class BackupListAdapter(val context: Context) : RecyclerView.Adapter<BackupListAdapter.Holder>() {

    private val backupList = ArrayList<LawnchairBackup>()
    private val backupMetaLoaderList = ArrayList<LawnchairBackup.MetaLoader>()

    var callbacks: Callbacks? = null

    fun setData(data: List<LawnchairBackup>){
        backupList.clear()
        data.forEach {
            backupList.add(it)
            backupMetaLoaderList.add(LawnchairBackup.MetaLoader(it))
        }
    }

    fun addItem(backup: LawnchairBackup) {
        backupList.add(0, backup)
        backupMetaLoaderList.add(0, LawnchairBackup.MetaLoader(backup))
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        backupList.removeAt(position)
        backupMetaLoaderList.removeAt(position)
        notifyItemRemoved(position + 1)
    }

    fun toUriList(): List<Uri> {
        return backupList.map { it -> it.uri }
    }

    operator fun get(position: Int) = backupList[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(getLayoutId(viewType), parent, false)
        return when (viewType) {
            TYPE_MENU -> MenuHolder(view)
            TYPE_ITEM -> ItemHolder(view)
            else -> Holder(view)
        }
    }

    private fun getLayoutId(viewType: Int) = when (viewType) {
        TYPE_MENU -> R.layout.backup_menu
        TYPE_ITEM -> R.layout.backup_item
        else -> R.layout.backup_blank
    }

    override fun getItemCount() = if (backupList.isEmpty()) 2 else backupList.size + 1

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(position)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_MENU
            else -> if (backupList.isEmpty()) TYPE_BLANK else TYPE_ITEM
        }
    }

    open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(position: Int) {

        }
    }

    inner class MenuHolder(itemView: View) : Holder(itemView), View.OnClickListener {

        init {
            itemView.findViewById<View>(R.id.action_new_backup).setOnClickListener(this)
            itemView.findViewById<View>(R.id.action_restore_backup).setOnClickListener(this)
        }

        override fun onClick(v: View) {
            when (v.id) {
                R.id.action_new_backup -> callbacks?.openBackup()
                R.id.action_restore_backup -> callbacks?.openRestore()
            }
        }
    }

    inner class ItemHolder(itemView: View) : Holder(itemView), LawnchairBackup.MetaLoader.Callback,
            View.OnClickListener, View.OnLongClickListener {

        private val title = itemView.findViewById<TextView>(android.R.id.title)
        private var indicator: FiftyShadesOf? = null
        private var metaLoader: LawnchairBackup.MetaLoader? = null
            set(value) {
                field?.callback = null
                value?.callback = this
                field = value
            }

        private val backupItem = itemView.findViewById<View>(R.id.backup_item)

        init {
            backupItem.setOnClickListener(this)
            backupItem.setOnLongClickListener(this)
        }

        override fun bind(position: Int) {
            indicator?.stop()
            indicator = FiftyShadesOf.with(context)
                    .on(title)
                    .fadein(true)
                    .start()
            metaLoader = backupMetaLoaderList[position - 1]
            metaLoader?.loadMeta()
            backupItem.isEnabled = false
            title.text = context.getString(R.string.backup_loading)
        }

        override fun onMetaLoaded() {
            indicator?.stop()
            backupItem.isEnabled = true
            title.text = metaLoader?.meta?.name ?: context.getString(R.string.backup_invalid)
        }

        override fun onClick(v: View) {
            callbacks?.openRestore(adapterPosition - 1)
        }

        override fun onLongClick(v: View?): Boolean {
            callbacks?.openEdit(adapterPosition - 1)
            return true
        }
    }

    interface Callbacks {

        fun openEdit(position: Int)
        fun openRestore(position: Int)
        fun openRestore()
        fun openBackup()
    }

    companion object {
        const val TYPE_MENU = 0
        const val TYPE_ITEM = 1
        const val TYPE_BLANK = 2
    }
}