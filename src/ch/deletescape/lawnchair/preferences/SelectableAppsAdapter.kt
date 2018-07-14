package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.view.View
import com.android.launcher3.AppFilter
import com.android.launcher3.util.ComponentKey
import kotlin.reflect.KMutableProperty0

abstract class SelectableAppsAdapter(context: Context, private val callback: Callback? = null, filter: AppFilter? = null)
    : AppsAdapter(context, null, filter) {

    private val selections = HashSet<ComponentKey>()

    init {
        callback?.onSelectionsChanged(0)
    }

    override fun onAppsListLoaded() {
        selections.addAll(getInitialSelections())
        super.onAppsListLoaded()
        callback?.onSelectionsChanged(selections.size)
    }

    override fun onBindApp(app: AppsAdapter.App, holder: AppHolder) {
        super.onBindApp(app, holder)
        holder.checkBox.visibility = View.VISIBLE
        holder.checkBox.isChecked = isSelected(app.key)
    }

    override fun onClickApp(position: Int, holder: AppHolder) {
        super.onClickApp(position, holder)
        toggleSelection(position)
        holder.checkBox.isChecked = isSelected(apps[position].key)
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

    interface Callback {

        fun onSelectionsChanged(newSize: Int)
    }

    companion object {

        fun ofProperty(context: Context, property: KMutableProperty0<Set<String>>,
                       callback: Callback? = null, filter: AppFilter? = null)
                = object : SelectableAppsAdapter(context, callback, filter) {

            override fun getInitialSelections() = HashSet(property.get().map { ComponentKey(context, it) })

            override fun setSelections(selections: Set<ComponentKey>) {
                property.set(HashSet(selections.map { it.toString() }))
            }
        }
    }
}
