package app.lawnchair.wallpaper

import android.annotation.TargetApi
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.lawnchair.wallpaper.WallpaperColorsCompat.Companion.HINT_SUPPORTS_DARK_TEXT
import app.lawnchair.wallpaper.WallpaperColorsCompat.Companion.HINT_SUPPORTS_DARK_THEME

@TargetApi(Build.VERSION_CODES.S)
internal class WallpaperManagerCompatVS(context: Context) : WallpaperManagerCompat(context) {

    override var wallpaperColors: WallpaperColorsCompat? = null
        private set

    init {
        wallpaperManager.addOnColorsChangedListener(
            { colors, which ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    update(colors)
                    notifyChange()
                }
            },
            Handler(Looper.getMainLooper()),
        )
        update(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
    }

    /**
     * Updates [wallpaperColors] from the platform colors of the system wallpaper.
     *
     * When the platform reports no colors (as on Samsung One UI), falls back to the seed color
     * One UI extracted for the wallpaper; if that is unavailable too, the colors are cleared.
     */
    private fun update(wallpaperColors: WallpaperColors?) {
        if (wallpaperColors == null) {
            // One UI returns no WallpaperColors to third-party apps, which made every
            // wallpaper-based color setting silently fall back to the default blue.
            // Samsung still exposes the seed it computed for the current wallpaper.
            this.wallpaperColors = getSamsungSeedColor()?.let { WallpaperColorsCompat(it, 0) }
            return
        }
        val platformHints = wallpaperManager
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.colorHints ?: 0
        var hints = 0
        if ((platformHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0) {
            hints = hints or HINT_SUPPORTS_DARK_TEXT
        }
        if ((platformHints and WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0) {
            hints = hints or HINT_SUPPORTS_DARK_THEME
        }
        this.wallpaperColors = WallpaperColorsCompat(wallpaperColors.primaryColor.toArgb(), hints)
    }

    /**
     * One UI's hidden `WallpaperManager.getSeedColors(int which)`, which returns the seed
     * colors Samsung extracted from the wallpaper. Returns null on other builds.
     */
    private fun getSamsungSeedColor(): Int? = try {
        val seeds = WallpaperManager::class.java
            .getMethod("getSeedColors", Int::class.javaPrimitiveType)
            .invoke(wallpaperManager, WallpaperManager.FLAG_SYSTEM) as? IntArray
        seeds?.firstOrNull { it != 0 }
    } catch (_: Exception) {
        null
    }
}
