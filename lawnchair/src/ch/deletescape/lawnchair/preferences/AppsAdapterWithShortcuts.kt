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

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.iconpack.LawnchairIconProvider
import ch.deletescape.lawnchair.isVisible
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey

open class AppsAdapterWithShortcuts(
        private val context: Context,
        private val callback: Callback? = null,
        private val filter: AppFilter? = null)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    companion object {
        private const val TYPE_LOADING = 0
        private const val TYPE_APP = 1
        private const val TYPE_SHORTCUT = 2

        private val iconDpi = LauncherAppState.getInstanceNoCreate().invariantDeviceProfile.fillResIconDpi

        private var iconProvider: IconProvider? = null
    }

    var items = ArrayList<Item>().apply { add(LoadingItem()) }
    val apps = ArrayList<AppItem>()
    val handler = Handler()
    val shortcutManager = DeepShortcutManager.getInstance(context)

    init {
        if (iconProvider == null) {
            iconProvider = IconProvider.newInstance(context)
        }
        Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(::loadAppsList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_APP -> AppHolder(layoutInflater.inflate(R.layout.app_item, parent, false))
            TYPE_SHORTCUT -> ShortcutHolder(layoutInflater.inflate(R.layout.app_item, parent, false))
            else -> LoadingHolder(layoutInflater.inflate(R.layout.adapter_loading, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppHolder) {
            holder.bind(items[position] as AppItem)
        } else if (holder is ShortcutHolder) {
            holder.bind(items[position] as ShortcutItem)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AppItem -> TYPE_APP
            is ShortcutItem -> TYPE_SHORTCUT
            else -> TYPE_LOADING
        }
    }

    protected open fun loadAppsList() {
        val apps = getAppsList(context)
                .sortedBy { it.label.toString().toLowerCase() }
                .map { AppItem(context, it) }
        handler.postAtFrontOfQueue { onAppsListLoaded(apps) }
    }

    protected open fun onAppsListLoaded(apps: List<AppItem>) {
        items.clear()
        items.addAll(apps)
        this.apps.addAll(apps)
        notifyDataSetChanged()
    }

    open fun onClickApp(position: Int) {
        val item = items[position]
        if (item is AppItem) {
            callback?.onAppSelected(item)
        } else if (item is ShortcutItem) {
            callback?.onShortcutSelected(item)
        }
    }

    fun onToggleApp(position: Int) {
        val app = items[position] as? AppItem
        if (app != null) {
            app.expanded = !app.expanded
            val oldItems = items
            val newItems = ArrayList<Item>()
            apps.forEach {
                newItems.add(it)
                if (it.expanded) {
                    newItems.addAll(it.shortcuts)
                }
            }
            items = newItems
            val diffResult = DiffUtil.calculateDiff(DiffCallback(oldItems, newItems))
            diffResult.dispatchUpdatesTo(this)
        }
    }

    private fun getAppsList(context: Context): List<LauncherActivityInfo> {
        val apps = ArrayList<LauncherActivityInfo>()
        val profiles = UserManagerCompat.getInstance(context).userProfiles
        val launcherAppsCompat = LauncherAppsCompat.getInstance(context)
        profiles.forEach { apps += launcherAppsCompat.getActivityList(null, it) }
        return if (filter != null) {
            apps.filter { filter.shouldShowApp(it.componentName, it.user) }
        } else {
            apps
        }
    }

    interface Item

    inner class AppItem(context: Context, val info: LauncherActivityInfo) : Item {

        val iconDrawable: Drawable
        val key = ComponentKey(info.componentName, info.user)
        val shortcuts = loadShortcuts()
        val hasShortcuts get() = !shortcuts.isEmpty()
        var expanded = false

        init {
            val appInfo = AppInfo(context, info, info.user)
            LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(appInfo, false)
            iconDrawable = BitmapDrawable(context.resources, appInfo.iconBitmap)
        }

        private fun loadShortcuts(): List<ShortcutItem> {
            val shortcuts = shortcutManager.queryForComponent(key) as? List<ShortcutInfoCompat> ?: emptyList()
            return shortcuts.map { ShortcutItem(it) }
        }

        fun clone(context: Context, expanded: Boolean) = AppItem(context, info).also { it.expanded = expanded }
    }

    inner class ShortcutItem(val info: ShortcutInfoCompat) : Item {

        val label = if (!TextUtils.isEmpty(info.longLabel)) info.longLabel else info.shortLabel
        // TODO: debug why wrong icons are loaded from the provider at times
        val iconDrawable = if (iconProvider is LawnchairIconProvider && false) {
                (iconProvider as LawnchairIconProvider).getIcon(info, iconDpi)
            } else {
                shortcutManager.getShortcutIconDrawable(info, DisplayMetrics.DENSITY_XXHIGH)
            }
    }

    class LoadingItem : Item

    inner class AppHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener, ValueAnimator.AnimatorUpdateListener {

        private val label: TextView = itemView.findViewById(R.id.label)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val caretContainer: View = itemView.findViewById(R.id.caretContainer)
        private val caretView: ImageView = itemView.findViewById(R.id.caretImageView)
        private val caretDrawable = CaretDrawable(context).apply { caretProgress = CaretDrawable.PROGRESS_CARET_POINTING_DOWN }
        private var caretPointingUp = false
            set(value) {
                animator?.cancel()
                field = value
            }
        private var animator: ValueAnimator? = null
            set(value) {
                field?.cancel()
                field = value
                field?.addUpdateListener(this)
                field?.start()
            }

        init {
            itemView.setOnClickListener(this)
            caretView.setImageDrawable(caretDrawable)
            caretContainer.setOnClickListener(this)
        }

        fun bind(app: AppItem) {
            label.text = app.info.label
            icon.setImageDrawable(app.iconDrawable)
            caretContainer.isVisible = app.hasShortcuts
            caretPointingUp = app.expanded
            caretDrawable.caretProgress = if (caretPointingUp) CaretDrawable.PROGRESS_CARET_POINTING_UP else CaretDrawable.PROGRESS_CARET_POINTING_DOWN
        }

        override fun onClick(v: View) {
            if (v == itemView) {
                onClickApp(adapterPosition)
            } else if (v == caretContainer) {
                onToggleApp(adapterPosition)
                animateCaretPointingUp(!caretPointingUp)
            }
        }

        private fun animateCaretPointingUp(pointingUp: Boolean) {
            caretPointingUp = pointingUp
            animator = ObjectAnimator.ofFloat(caretDrawable.caretProgress, if (pointingUp) CaretDrawable.PROGRESS_CARET_POINTING_UP else CaretDrawable.PROGRESS_CARET_POINTING_DOWN)
                    .setDuration(200)
        }

        override fun onAnimationUpdate(animator: ValueAnimator) {
            caretDrawable.caretProgress = animator.animatedValue as Float
        }
    }

    inner class ShortcutHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val label: TextView = itemView.findViewById(R.id.label)
        private val icon: ImageView = itemView.findViewById(R.id.shortcutIcon)

        init {
            itemView.setOnClickListener(this)
            itemView.findViewById<View>(R.id.icon).isVisible = false
            icon.isVisible = true
        }

        fun bind(shortcut: ShortcutItem) {
            label.text = shortcut.label
            icon.setImageDrawable(shortcut.iconDrawable)
        }

        override fun onClick(v: View) {
            onClickApp(adapterPosition)
        }
    }

    inner class LoadingHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            val progressBar = itemView.findViewById<ProgressBar>(R.id.progress)
            progressBar.indeterminateTintList = ColorStateList.valueOf(ColorEngine.getInstance(context).accent)
        }
    }

    class DiffCallback(val old: List<Item>, val new: List<Item>) : DiffUtil.Callback() {

        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = old[oldItemPosition]
            val newItem = new[newItemPosition]
            if (oldItem is AppItem && newItem is AppItem) {
                return oldItem.info == newItem.info
            }
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] === new[newItemPosition]
        }

    }

    interface Callback {

        fun onAppSelected(app: AppItem)
        fun onShortcutSelected(shortcut: ShortcutItem)
    }
}
