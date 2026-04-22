package app.lawnchair.views.overlay

import android.annotation.StringRes
import com.android.launcher3.R

enum class FullScreenOverlayMode(val value: String, @StringRes val labelRes: Int) {
    NONE("none", R.string.overlay_none),
    FADE_IN("fade_in", R.string.overlay_fade_in),
    SUCK_IN("suck_in", R.string.overlay_suck_in),
    ;

    companion object {
        fun fromValue(value: String): FullScreenOverlayMode {
            return entries.find { it.value == value } ?: FADE_IN
        }
    }
}
