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

    private fun update(wallpaperColors: WallpaperColors?) {
        if (wallpaperColors == null) {
            this.wallpaperColors = null
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
}
