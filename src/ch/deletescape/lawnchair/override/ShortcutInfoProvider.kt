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
        return (info.customTitle ?: info.title) as String
    }

    override fun getDefaultTitle(info: ShortcutInfo): String {
        return info.title as String
    }

    override fun getCustomTitle(info: ShortcutInfo): String? {
        return info.customTitle as String?
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
            val bitmap = LauncherIcons.createBadgedIconBitmap(drawable, info.user, context, Build.VERSION_CODES.O_MR1)
            info.setIcon(context, bitmap)
        } else {
            info.setIcon(context, null)
        }
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