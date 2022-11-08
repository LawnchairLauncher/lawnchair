package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.icons.R
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.AdaptiveIconDrawable
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import com.android.launcher3.icons.ThemedIconDrawable
import android.graphics.drawable.ColorDrawable
import app.lawnchair.icons.*
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME



class IconPackProvider(private val context: Context) {

    private val systemIconPack = SystemIconPack(context)
    private val iconPacks = mutableMapOf<String, IconPack?>()
    private val themedIconPacks = context.resources.getStringArray(R.array.themed_icon_packs)

    fun getIconPackOrSystem(packageName: String): IconPack? {
        if (packageName.isEmpty()) return systemIconPack
        return getIconPack(packageName)
    }

    fun getIconPack(packageName: String): IconPack? {
        if (packageName.isEmpty()) {
            return null
        }
        return iconPacks.getOrPut(packageName) {
            try {
                CustomIconPack(context, packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun getClockMetadata(iconEntry: IconEntry): ClockMetadata? {
        val iconPack = getIconPackOrSystem(iconEntry.packPackageName) ?: return null
        return iconPack.getClock(iconEntry)
    }

    fun getDrawable(iconEntry: IconEntry, iconDpi: Int, user: UserHandle): Drawable? {
        val iconPack = getIconPackOrSystem(iconEntry.packPackageName) ?: return null
        iconPack.loadBlocking()
        val drawable = iconPack.getIcon(iconEntry, iconDpi) ?: return null
        if (
            context.isThemedIconsEnabled() && iconEntry.packPackageName in themedIconPacks
        ) {
            val themedColors: IntArray = ThemedIconDrawable.getThemedColors(context)
            val res = context.packageManager.getResourcesForApplication(iconEntry.packPackageName)
            @SuppressLint("DiscouragedApi")
            val resId = res.getIdentifier(iconEntry.name, "drawable", iconEntry.packPackageName)
            val bg: Drawable = ColorDrawable(themedColors[0])
            val td = ThemedIconDrawable.ThemeData(res, iconEntry.packPackageName, resId)
            val fg = td.wrapDrawable(drawable, 0)
            return if (fg is AdaptiveIconDrawable) {
                val foregroundDr: Drawable = fg.getForeground()
                foregroundDr.setTint(themedColors[1])
                CustomAdaptiveIconDrawable(bg, foregroundDr)
            } else {
                val iconFromPack = InsetDrawable(drawable, .3f)
                iconFromPack.setTint(themedColors[1])
                td.wrapDrawable(CustomAdaptiveIconDrawable(bg, iconFromPack), 0)
            }
        }
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
