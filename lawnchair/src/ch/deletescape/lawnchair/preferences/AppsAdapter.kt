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
import ch.deletescape.lawnchair.comparing
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.util.ComponentKey

open class AppsAdapter(
        private val context: Context,
        private val callback: Callback? = null,
        private val filter: AppFilter? = null)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_LOADING = 0
    private val TYPE_ITEM = 1

    var isLoaded = false
    val apps = ArrayList<App>()

    val handler = Handler()

    open val comparator = defaultComparator

    fun postLoadApps() {
        Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(::loadAppsList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (getItemViewType(0) == TYPE_ITEM) {
            AppHolder(layoutInflater.inflate(R.layout.app_item, parent, false))
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

    protected open fun loadAppsList() {
        apps.addAll(getAppsList(context)
                .map { App(context, it) }
                .sortedWith(comparator))
        handler.postAtFrontOfQueue(::onAppsListLoaded)
    }

    protected open fun onAppsListLoaded() {
        isLoaded = true
        notifyDataSetChanged()
    }

    open fun onBindApp(app: App, holder: AppHolder) {

    }

    open fun onClickApp(position: Int, holder: AppHolder) {
        callback?.onAppSelected(apps[position])
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

    class App(context: Context, val info: LauncherActivityInfo) {

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
        val checkBox: CheckBox = itemView.findViewById(R.id.check)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(position: Int) {
            val app = apps[position]

            label.text = app.info.label
            icon.setImageDrawable(app.iconDrawable)

            onBindApp(app, this)
        }

        override fun onClick(v: View) {
            onClickApp(adapterPosition, this)
        }
    }

    inner class LoadingHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface Callback {

        fun onAppSelected(app: App)
    }

    companion object {
        val defaultComparator = comparing<App, String> { it.info.label.toString().toLowerCase() }
    }
}
