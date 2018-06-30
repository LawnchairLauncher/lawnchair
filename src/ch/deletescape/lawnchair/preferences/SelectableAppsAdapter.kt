package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.android.launcher3.AppInfo
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.util.ComponentKey
import kotlin.reflect.KMutableProperty0

abstract class SelectableAppsAdapter(private val context: Context, private val callback: Callback? = null)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val TYPE_LOADING = 0
    val TYPE_ITEM = 1

    var isLoaded = false
    val apps = ArrayList<SelectableApp>()
    val selections = HashSet<ComponentKey>()

    val handler = Handler()

    init {
        Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(::loadAppsList)
        callback?.onSelectionsChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (getItemViewType(0) == TYPE_ITEM) {
            AppHolder(layoutInflater.inflate(R.layout.hide_item, parent, false))
        } else {
            LoadingHolder(layoutInflater.inflate(R.layout.adapter_loading, parent, false))
        }
    }

    override fun getItemCount() = if (isLoaded) apps.size else 1

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppHolder) {
            holder.bind(position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isLoaded) TYPE_ITEM else TYPE_LOADING
    }

    private fun loadAppsList() {
        apps.addAll(getAppsList(context).apply {
            sortBy { it.label.toString().toLowerCase() }
        }.map { SelectableApp(context, it) })
        selections.addAll(getInitialSelections())
        handler.postAtFrontOfQueue(::onAppsListLoaded)
    }

    private fun onAppsListLoaded() {
        isLoaded = true
        notifyDataSetChanged()
        callback?.onSelectionsChanged(selections.size)
    }

    private fun getAppsList(context: Context): ArrayList<LauncherActivityInfo> {
        val apps = ArrayList<LauncherActivityInfo>()
        val profiles = UserManagerCompat.getInstance(context).userProfiles
        val launcherAppsCompat = LauncherAppsCompat.getInstance(context)
        profiles.forEach { apps += launcherAppsCompat.getActivityList(null, it) }
        return apps
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

    abstract fun getInitialSelections(): Set<ComponentKey>

    abstract fun setSelections(selections: Set<ComponentKey>)

    class SelectableApp(context: Context, val info: LauncherActivityInfo) {

        val iconDrawable: Drawable
        val key = ComponentKey(info.componentName, info.user)

        init {
            val appInfo = AppInfo(context, info, info.user)
            LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(appInfo, false)
            iconDrawable = BitmapDrawable(context.resources, appInfo.iconBitmap)
        }
    }

    inner class AppHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val label: TextView = itemView.findViewById(R.id.label)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val checkBox: CheckBox = itemView.findViewById(R.id.check)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(position: Int) {
            val app = apps[position]

            label.text = app.info.label
            icon.setImageDrawable(app.iconDrawable)

            checkBox.isChecked = isSelected(app.key)
        }

        override fun onClick(v: View) {
            toggleSelection(adapterPosition)
            checkBox.isChecked = isSelected(apps[adapterPosition].key)
        }
    }

    inner class LoadingHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface Callback {

        fun onSelectionsChanged(newSize: Int)
    }

    companion object {

        fun ofProperty(context: Context, property: KMutableProperty0<Set<String>>, callback: Callback? = null)
                = object : SelectableAppsAdapter(context, callback) {

            override fun getInitialSelections() = HashSet(property.get().map { ComponentKey(context, it) })

            override fun setSelections(selections: Set<ComponentKey>) {
                property.set(HashSet(selections.map { it.toString() }))
            }
        }
    }
}
