package app.lawnchair.theme

import android.annotation.TargetApi
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.android.systemui.shared.system.TonalCompat

@TargetApi(Build.VERSION_CODES.P)
internal class WallpaperManagerCompatVP(context: Context) : WallpaperManagerCompat(context) {

    private val wallpaperManager = context.getSystemService<WallpaperManager>()!!
    private val tonalCompat = TonalCompat(context)

    private lateinit var extractionInfo: TonalCompat.ExtractionInfo

    init {
        wallpaperManager.addOnColorsChangedListener(
            { colors, which ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    update(colors)
                    notifyChange()
                }
            },
            Handler(Looper.getMainLooper())
        )
        update(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
    }

    override val colorHints: Int
        get() {
            var hints = 0
            if (extractionInfo.supportsDarkText) {
                hints = hints or HINT_SUPPORTS_DARK_TEXT
            }
            if (extractionInfo.supportsDarkTheme) {
                hints = hints or HINT_SUPPORTS_DARK_THEME
            }
            return hints
        }

    private fun update(wallpaperColors: WallpaperColors?) {
        extractionInfo = tonalCompat.extractDarkColors(wallpaperColors)
    }
}
