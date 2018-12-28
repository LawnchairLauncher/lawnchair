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
import android.os.Build
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.graphics.LauncherIcons

class ShortcutInfoProvider(private val context: Context) : CustomInfoProvider<ShortcutInfo>() {

    private val launcherApps by lazy { LauncherAppsCompat.getInstance(context) }

    override fun getTitle(info: ShortcutInfo): String {
        return (info.customTitle ?: info.title).toString()
    }

    override fun getDefaultTitle(info: ShortcutInfo): String {
        return info.title.toString()
    }

    override fun getCustomTitle(info: ShortcutInfo): String? {
        return info.customTitle?.toString()
    }

    fun getSwipeUpAction(info: ShortcutInfo): String? {
        return info.swipeUpAction
    }

    override fun setTitle(info: ShortcutInfo, title: String?) {
        info.setTitle(context, title)
    }

    override fun setIcon(info: ShortcutInfo, entry: IconPackManager.CustomIconEntry?) {
        info.setIconEntry(context, entry)
        if (entry != null) {
            val launcherActivityInfo = getLauncherActivityInfo(info)
            val iconCache = LauncherAppState.getInstance(context).iconCache
            val drawable = iconCache.getFullResIcon(launcherActivityInfo, info, false)
            val bitmap = LauncherIcons.obtain(context).createBadgedIconBitmap(drawable, info.user, Build.VERSION_CODES.O_MR1)
            info.setIcon(context, bitmap.icon)
        } else {
            info.setIcon(context, null)
        }
    }

    fun setSwipeUpAction(info: ShortcutInfo, action: String?) {
        info.setSwipeUpAction(context, action)
    }

    override fun getIcon(info: ShortcutInfo): IconPackManager.CustomIconEntry? {
        return info.customIconEntry
    }

    private fun getLauncherActivityInfo(info: ShortcutInfo): LauncherActivityInfo? {
        return launcherApps.resolveActivity(info.getIntent(), info.user)
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: ShortcutInfoProvider? = null

        fun getInstance(context: Context): ShortcutInfoProvider {
            if (INSTANCE == null) {
                INSTANCE = ShortcutInfoProvider(context.applicationContext)
            }
            return INSTANCE!!
        }
    }
}