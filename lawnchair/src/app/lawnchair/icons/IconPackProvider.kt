package app.lawnchair.icons

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.util.MainThreadInitializedObject

class IconPackProvider(private val context: Context) {

    private val iconPacks = mutableMapOf<String, IconPack?>()

    fun getIconPack(packageName: String): IconPack? {
        if (packageName == "") {
            return null
        }
        return iconPacks.getOrPut(packageName) {
            try {
                val packResources = context.packageManager.getResourcesForApplication(packageName)
                CustomIconPack(context, packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun getDrawable(iconEntry: IconEntry, iconDpi: Int, user: UserHandle): Drawable? {
        val iconPack = getIconPack(iconEntry.packPackageName) ?: return null
        val drawable = iconPack.getIcon(iconEntry, iconDpi) ?: return null
        val clockMetadata = if (user == Process.myUserHandle()) iconPack.getClock(iconEntry) else null
        if (clockMetadata != null) {
            val clockDrawable = ClockDrawableWrapper.forMeta(Build.VERSION.SDK_INT, clockMetadata) {
                drawable
            }
            if (clockDrawable != null) {
                return clockDrawable
            }
        }
        return drawable
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconPackProvider)
    }
}
