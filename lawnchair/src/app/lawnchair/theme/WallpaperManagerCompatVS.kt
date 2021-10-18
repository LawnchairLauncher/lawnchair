package app.lawnchair.theme

import android.annotation.TargetApi
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

@TargetApi(Build.VERSION_CODES.S)
internal class WallpaperManagerCompatVS(context: Context) : WallpaperManagerCompat(context) {

    private val wallpaperManager = context.getSystemService<WallpaperManager>()!!

    override val colorHints: Int
        get() {
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
            return hints
        }
}
