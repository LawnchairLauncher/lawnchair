package app.lawnchair.util

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build

fun isWallpaperDark(context: Context): Boolean {
    val wallpaperManager = WallpaperManager.getInstance(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val colors: WallpaperColors? =
            wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        colors?.primaryColor?.toArgb()?.let { argb ->
            val darkness =
                1 - (0.299 * Color.red(argb) + 0.587 * Color.green(argb) + 0.114 * Color.blue(argb)) / 255
            darkness >= 0.5
        } ?: false
    } else {
        false
    }
}


