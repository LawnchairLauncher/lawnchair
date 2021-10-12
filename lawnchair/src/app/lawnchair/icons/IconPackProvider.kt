package app.lawnchair.icons

import android.content.Context
import android.content.pm.PackageManager
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
                IconPack(context, packageName, packResources)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconPackProvider)
    }
}
