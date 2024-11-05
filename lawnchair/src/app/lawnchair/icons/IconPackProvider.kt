package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.icons.ThemedIconDrawable
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable

class IconPackProvider(private val context: Context) : SafeCloseable {

    private val iconPacks = mutableMapOf<String, IconPack?>()

    fun getIconPackOrSystem(packageName: String): IconPack? {
        if (packageName.isEmpty()) return SystemIconPack(context, packageName)
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
        val shouldTintBackgrounds = context.shouldTintIconPackBackgrounds()
        val clockMetadata =
            if (user == Process.myUserHandle()) iconPack.getClock(iconEntry) else null
        try {
            if (clockMetadata != null) {
                val clockDrawable: ClockDrawableWrapper =
                    ClockDrawableWrapper.forMeta(Build.VERSION.SDK_INT, clockMetadata) {
                        if (shouldTintBackgrounds) {
                            wrapThemedData(
                                packageManager,
                                iconEntry,
                                drawable,
                            )
                        } else {
                            drawable
                        }
                    }
                return if (shouldTintBackgrounds && context.shouldTransparentBGIcons()) {
                    clockDrawable.foreground
                } else {
                    CustomAdaptiveIconDrawable(
                        clockDrawable.background,
                        clockDrawable.foreground,
                    )
                }
            }
        } catch (t: Throwable) {
            // Ignore
        }

        if (shouldTintBackgrounds) {
            return wrapThemedData(packageManager, iconEntry, drawable)
        }
        return drawable
    }

    private fun wrapThemedData(
        packageManager: PackageManager,
        iconEntry: IconEntry,
        drawable: Drawable,
    ): Drawable? {
        val themedColors: IntArray = ThemedIconDrawable.getThemedColors(context)
        try {
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
        } catch (_: Exception) {
            return drawable
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconPackProvider)
    }
}
