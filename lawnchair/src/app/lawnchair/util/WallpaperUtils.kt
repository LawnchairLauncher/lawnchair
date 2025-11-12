package app.lawnchair.util

import android.content.Context
import android.graphics.Color
import app.lawnchair.wallpaper.WallpaperManagerCompat

fun isWallpaperDark(context: Context): Boolean {
    val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    val colors = wallpaperManager.wallpaperColors
    val primaryColor = colors?.primaryColor ?: return false
    val brightness = getBrightness(primaryColor)
    return brightness < 0.5
}
private fun getBrightness(color: Int): Double {
    return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
}
