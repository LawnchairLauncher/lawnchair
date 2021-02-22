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
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.AppInfo
import com.android.launcher3.FolderInfo
import com.android.launcher3.ItemInfo
import com.android.launcher3.ShortcutInfo

abstract class CustomInfoProvider<in T : ItemInfo>(val context: Context) {

    abstract fun getTitle(info: T): String

    abstract fun getDefaultTitle(info: T): String

    abstract fun getCustomTitle(info: T): String?

    abstract fun setTitle(info: T, title: String?)

    open fun supportsIcon() = true

    abstract fun setIcon(info: T, entry: IconPackManager.CustomIconEntry?)

    abstract fun getIcon(info: T): IconPackManager.CustomIconEntry?

    open fun supportsSwipeUp(info: T) = false

    open fun setSwipeUpAction(info: T, action: String?) {
        TODO("not implemented")
    }

    open fun getSwipeUpAction(info: T): String? {
        TODO("not implemented")
    }

    open fun supportsBadgeVisible(info: T) = false

    open fun setBadgeVisible(info: T, visible: Boolean) {
        TODO("not implemented")
    }

    open fun getBadgeVisible(info: T): Boolean {
        TODO("not implemented")
    }

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T : ItemInfo> forItem(context: Context, info: ItemInfo?): CustomInfoProvider<T>? {
            return when (info) {
                is AppInfo -> AppInfoProvider.getInstance(context)
                is ShortcutInfo -> ShortcutInfoProvider.getInstance(context)
                is FolderInfo -> FolderInfoProvider.getInstance(context)
                else -> null
            } as CustomInfoProvider<T>?
        }

        fun isEditable(info: ItemInfo): Boolean {
            return info is AppInfo || (info is ShortcutInfo && !info.hasPromiseIconUi())
                    || info is FolderInfo
        }
    }
}
