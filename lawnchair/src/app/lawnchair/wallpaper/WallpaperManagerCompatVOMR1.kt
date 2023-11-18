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
import com.android.systemui.shared.system.TonalCompat

@TargetApi(Build.VERSION_CODES.O_MR1)
internal class WallpaperManagerCompatVOMR1(context: Context) : WallpaperManagerCompat(context) {

    private val tonalCompat = TonalCompat(context)

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
        val extractionInfo = tonalCompat.extractDarkColors(wallpaperColors)
        var hints = 0
        if (extractionInfo.supportsDarkText) {
            hints = hints or HINT_SUPPORTS_DARK_TEXT
        }
        if (extractionInfo.supportsDarkTheme) {
            hints = hints or HINT_SUPPORTS_DARK_THEME
        }
        this.wallpaperColors = WallpaperColorsCompat(wallpaperColors.primaryColor.toArgb(), hints)
    }
}
