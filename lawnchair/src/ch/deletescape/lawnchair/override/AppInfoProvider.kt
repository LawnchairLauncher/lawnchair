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

package ch.deletescape.lawnchair.override

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherActivityInfo
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.AppInfo
import com.android.launcher3.LauncherAppState
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.util.ComponentKey

class AppInfoProvider private constructor(context: Context) : CustomInfoProvider<AppInfo>(context) {

    private val prefs = context.lawnchairPrefs
    private val launcherApps by lazy { LauncherAppsCompat.getInstance(context) }

    override fun getTitle(info: AppInfo): String {
        return prefs.customAppName[info.toComponentKey()] ?: info.title.toString()
    }

    override fun getDefaultTitle(info: AppInfo): String {
        val app = getLauncherActivityInfo(info)
        return app?.label?.toString() ?: "" // TODO: can this really be null?
    }

    override fun getCustomTitle(info: AppInfo): String? {
        return prefs.customAppName[ComponentKey(info.componentName, info.user)]
    }

    fun getTitle(app: LauncherActivityInfo): CharSequence {
        return prefs.customAppName[getComponentKey(app)] ?: app.label
    }

    override fun setTitle(info: AppInfo, title: String?) {
        setTitle(info.toComponentKey(), title)
    }

    fun setTitle(key: ComponentKey, title: String?) {
        prefs.customAppName[key] = title
        LauncherAppState.getInstance(context).iconCache.updateIconsForPkg(key.componentName.packageName, key.user)
    }

    override fun setIcon(info: AppInfo, entry: IconPackManager.CustomIconEntry?) {
        setIcon(info.toComponentKey(), entry)
    }

    fun setIcon(key: ComponentKey, entry: IconPackManager.CustomIconEntry?) {
        prefs.customAppIcon[key] = entry
        LauncherAppState.getInstance(context).iconCache.updateIconsForPkg(key.componentName.packageName, key.user)
    }

    private fun getLauncherActivityInfo(info: AppInfo): LauncherActivityInfo? {
        return launcherApps.resolveActivity(info.getIntent(), info.user)
    }

    fun getCustomIconEntry(app: LauncherActivityInfo): IconPackManager.CustomIconEntry? {
        return getCustomIconEntry(getComponentKey(app))
    }

    fun getCustomIconEntry(key: ComponentKey): IconPackManager.CustomIconEntry? {
        return prefs.customAppIcon[key]
    }

    override fun getIcon(info: AppInfo): IconPackManager.CustomIconEntry? {
        return getCustomIconEntry(info.toComponentKey())
    }

    private fun getComponentKey(app: LauncherActivityInfo) = ComponentKey(app.componentName, app.user)

    companion object : SingletonHolder<AppInfoProvider, Context>(ensureOnMainThread(
            useApplicationContext(::AppInfoProvider)))
}