package app.lawnchair.wallpaper

import android.graphics.Color
import androidx.annotation.IntDef

data class WallpaperColorsCompat(
    val primaryColor: Color,
    @ColorsHints val colorHints: Int
) {

    companion object {

        const val HINT_SUPPORTS_DARK_TEXT = 1 shl 0
        const val HINT_SUPPORTS_DARK_THEME = 1 shl 1

        @IntDef(
            value = [HINT_SUPPORTS_DARK_TEXT, HINT_SUPPORTS_DARK_THEME],
            flag = true
        )
        @Retention(
            AnnotationRetention.SOURCE
        )
        annotation class ColorsHints
    }
}
