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
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.AdaptiveIconDrawable
import com.android.launcher3.icons.ThemedIconDrawable
import android.graphics.drawable.ColorDrawable
import app.lawnchair.icons.*
import app.lawnchair.util.getThemedIconPacksInstalled


class IconPackProvider(private val context: Context) {

    private val systemIconPack = SystemIconPack(context)
    private val iconPacks = mutableMapOf<String, IconPack?>()

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
        val packageManager = context.packageManager
        val drawable = iconPack.getIcon(iconEntry, iconDpi) ?: return null
        val themedIconPacks = packageManager.getThemedIconPacksInstalled(context)
        val isThemedIconsEnabled =
            context.isThemedIconsEnabled() && (iconEntry.packPackageName in themedIconPacks)
        val clockMetadata =
            if (user == Process.myUserHandle()) iconPack.getClock(iconEntry) else null
        if (clockMetadata != null) {
            val clockDrawable: ClockDrawableWrapper =
                ClockDrawableWrapper.forMeta(Build.VERSION.SDK_INT, clockMetadata) {
                    if (isThemedIconsEnabled) wrapThemedData(
                        packageManager,
                        iconEntry,
                        drawable
                    ) else drawable
                }
            if (clockDrawable != null) {
                return if (isThemedIconsEnabled && context.shouldTransparentBGIcons()) clockDrawable.foreground else CustomAdaptiveIconDrawable(
                    clockDrawable.background,
                    clockDrawable.foreground
                )
            }
        }
        if (isThemedIconsEnabled) {
            return wrapThemedData(packageManager, iconEntry, drawable)
        }
        return drawable
    }

    private fun wrapThemedData(
        packageManager: PackageManager,
        iconEntry: IconEntry,
        drawable: Drawable
    ): Drawable? {
        val themedColors: IntArray = ThemedIconDrawable.getThemedColors(context)
        val res = packageManager.getResourcesForApplication(iconEntry.packPackageName)

        @SuppressLint("DiscouragedApi")
        val resId = res.getIdentifier(iconEntry.name, "drawable", iconEntry.packPackageName)
        val bg: Drawable = ColorDrawable(themedColors[0])
        val td = ThemedIconDrawable.ThemeData(res, iconEntry.packPackageName, resId)
        return if (drawable is AdaptiveIconDrawable) {
            if (context.shouldTransparentBGIcons() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable.monochrome != null) {
                drawable.monochrome?.apply { setTint(themedColors[1]) }
            } else {
                val foregroundDr = drawable.foreground.apply { setTint(themedColors[1]) }
                CustomAdaptiveIconDrawable(bg, foregroundDr)
            }
        } else {
            val iconFromPack = InsetDrawable(drawable, .3f).apply { setTint(themedColors[1]) }
            td.wrapDrawable(CustomAdaptiveIconDrawable(bg, iconFromPack), 0)
        }
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconPackProvider)
    }
}
