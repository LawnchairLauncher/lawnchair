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

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.Build
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.LauncherAppState
import com.android.launcher3.WorkspaceItemInfo
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.icons.LauncherIcons

class ShortcutInfoProvider private constructor(context: Context) : CustomInfoProvider<WorkspaceItemInfo>(context) {

    private val launcherApps by lazy { LauncherAppsCompat.getInstance(context) }

    override fun getTitle(info: WorkspaceItemInfo): String {
        return (info.customTitle ?: info.title).toString()
    }

    override fun getDefaultTitle(info: WorkspaceItemInfo): String {
        return info.title.toString()
    }

    override fun getCustomTitle(info: WorkspaceItemInfo): String? {
        return info.customTitle?.toString()
    }

    override fun setTitle(info: WorkspaceItemInfo, title: String?) {
        info.setTitle(context, title)
    }

    override fun setIcon(info: WorkspaceItemInfo, entry: IconPackManager.CustomIconEntry?) {
        info.setIconEntry(context, entry)
        if (entry != null) {
            val launcherActivityInfo = getLauncherActivityInfo(info)
            val iconCache = LauncherAppState.getInstance(context).iconCache
            val drawable = iconCache.getFullResIcon(launcherActivityInfo, false)
            val bitmap = LauncherIcons.obtain(context).createBadgedIconBitmap(drawable, info.user, Build.VERSION_CODES.O_MR1)
            info.setIcon(context, bitmap.icon)
        } else {
            info.setIcon(context, null)
        }
    }

    override fun getIcon(info: WorkspaceItemInfo): IconPackManager.CustomIconEntry? {
        return info.customIconEntry
    }

    override fun supportsSwipeUp(info: WorkspaceItemInfo) = true

    override fun setSwipeUpAction(info: WorkspaceItemInfo, action: String?) {
        info.setSwipeUpAction(context, action)
    }

    override fun getSwipeUpAction(info: WorkspaceItemInfo): String? {
        return info.swipeUpAction
    }

    private fun getLauncherActivityInfo(info: WorkspaceItemInfo): LauncherActivityInfo? {
        return launcherApps.resolveActivity(info.getIntent(), info.user)
    }

    companion object : SingletonHolder<ShortcutInfoProvider, Context>(ensureOnMainThread(
            useApplicationContext(::ShortcutInfoProvider)))
}