package app.lawnchair.util

import android.content.Context
import android.graphics.Color
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat

fun isWallpaperDark(context: Context): Boolean {
    val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    val colors: WallpaperColorsCompat? = wallpaperManager.wallpaperColors
    return colors?.primaryColor?.let { argb ->
        val darkness =
            1 - (0.299 * Color.red(argb) + 0.587 * Color.green(argb) + 0.114 * Color.blue(argb)) / 255
        darkness >= 0.5
    } ?: false
}
