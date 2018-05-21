package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import com.google.android.apps.nexuslauncher.DynamicIconProvider

class LawnchairIconProvider(context: Context) : DynamicIconProvider(context) {

    private val iconPackManager = IconPackManager.getInstance(context)

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int, flattenDrawable: Boolean): Drawable {
        return iconPackManager.getIcon(launcherActivityInfo, iconDpi, flattenDrawable, this)
    }

    fun getDynamicIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int, flattenDrawable: Boolean): Drawable {
        return super.getIcon(launcherActivityInfo, iconDpi, flattenDrawable)
    }
}
