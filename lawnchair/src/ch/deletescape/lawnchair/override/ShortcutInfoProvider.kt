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
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.graphics.LauncherIcons

class ShortcutInfoProvider private constructor(context: Context) : CustomInfoProvider<ShortcutInfo>(context) {

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

    override fun getIcon(info: ShortcutInfo): IconPackManager.CustomIconEntry? {
        return info.customIconEntry
    }

    override fun supportsSwipeUp(info: ShortcutInfo) = true

    override fun setSwipeUpAction(info: ShortcutInfo, action: String?) {
        info.setSwipeUpAction(context, action)
    }

    override fun getSwipeUpAction(info: ShortcutInfo): String? {
        return info.swipeUpAction
    }

    override fun supportsBadgeVisible(info: ShortcutInfo) = when (info.itemType) {
        ITEM_TYPE_SHORTCUT, ITEM_TYPE_DEEP_SHORTCUT -> true
        // TODO if work badge is present
        else -> false
    }

    override fun setBadgeVisible(info: ShortcutInfo, visible: Boolean) {
        info.setBadgeVisible(context, visible)
    }

    override fun getBadgeVisible(info: ShortcutInfo): Boolean {
        return info.isBadgeVisible
    }

    private fun getLauncherActivityInfo(info: ShortcutInfo): LauncherActivityInfo? {
        return launcherApps.resolveActivity(info.getIntent(), info.user)
    }

    companion object : SingletonHolder<ShortcutInfoProvider, Context>(ensureOnMainThread(
            useApplicationContext(::ShortcutInfoProvider)))
}
