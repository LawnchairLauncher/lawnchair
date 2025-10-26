package app.lawnchair.icons

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject

@LauncherAppSingleton
class IconPackProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

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
    ): Drawable {
        val res = packageManager.getResourcesForApplication(iconEntry.packPackageName)
        val resId = res.getIdentifier(iconEntry.name, "drawable", iconEntry.packPackageName)
        val td = IconProvider.ThemeData(res, resId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            drawable is AdaptiveIconDrawable &&
            drawable.monochrome == null
        ) {
            return AdaptiveIconDrawable(
                drawable.background,
                drawable.foreground,
                td.loadPaddedDrawable(),
            )
        }
        return drawable
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconPackProvider)
    }
}
