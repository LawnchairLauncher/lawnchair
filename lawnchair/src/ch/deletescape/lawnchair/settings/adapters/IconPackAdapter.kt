package ch.deletescape.lawnchair.settings.adapters

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import ch.deletescape.lawnchair.settings.adapters.IconPackAdapter.CheckListener
import ch.deletescape.lawnchair.settings.views.IconPackListItem

class IconPackAdapter(private val dataSet: List<IconPackInfo>) :
    RecyclerView.Adapter<IconPackAdapter.ViewHolder>() {

    data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable?)

    // TODO: Store package name of currently applied icon pack in settings database. Also see `IconPackSettingsFragment`.
    private var selectedIconPackPackageName: String = ""

    fun interface CheckListener {
        fun onItemChecked(id: String)
    }

    private val checkListener = CheckListener { id ->
        if (selectedIconPackPackageName != id) {
            selectedIconPackPackageName = id
            // TODO: Use `DiffUtil` instead of `notifyDataSetChanged` to fix animations.
            notifyDataSetChanged()
        }
    }

    class ViewHolder(private val view: IconPackListItem, private val checkListener: CheckListener) :
        RecyclerView.ViewHolder(view) {
        fun bind(iconPackInfo: IconPackInfo, selectedIconPackPackageName: String) {
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            view.iconPackName = iconPackInfo.name
            view.iconPackPackageName = iconPackInfo.packageName
            view.iconPackIcon = iconPackInfo.icon
            view.isChecked = selectedIconPackPackageName == view.iconPackPackageName
            itemView.setOnClickListener { checkListener.onItemChecked(view.iconPackPackageName) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val iconPackListItem = IconPackListItem(parent.context)
        return ViewHolder(iconPackListItem, checkListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSet[position], selectedIconPackPackageName)
    }

    override fun getItemCount(): Int = dataSet.size

}