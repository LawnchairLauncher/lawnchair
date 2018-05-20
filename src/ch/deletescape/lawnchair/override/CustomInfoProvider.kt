package ch.deletescape.lawnchair.override

import android.content.Context
import com.android.launcher3.AppInfo
import com.android.launcher3.ItemInfo
import com.android.launcher3.ShortcutInfo

abstract class CustomInfoProvider<in T : ItemInfo> {

    abstract fun getTitle(info: T): String

    abstract fun getDefaultTitle(info: T): String

    abstract fun getCustomTitle(info: T): String?

    abstract fun setTitle(info: T, title: String?)

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T : ItemInfo> forItem(context: Context, info: ItemInfo): CustomInfoProvider<T> {
            return if (info is AppInfo) {
                AppInfoProvider.getInstance(context)
            } else {
                ShortcutInfoProvider.getInstance(context)
            } as CustomInfoProvider<T>
        }

        fun isEditable(info: ItemInfo): Boolean {
            return info is AppInfo || info is ShortcutInfo
        }
    }
}