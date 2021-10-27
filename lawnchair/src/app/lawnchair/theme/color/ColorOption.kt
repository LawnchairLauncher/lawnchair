package app.lawnchair.theme.color

import android.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities

sealed class ColorOption {

    abstract val isSupported: Boolean
    abstract val colorPreferenceEntry: ColorPreferenceEntry<ColorOption>

    object SystemAccent : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.system) },
            { LocalContext.current.getSystemAccent(false) },
            { LocalContext.current.getSystemAccent(true) }
        )

        override fun toString() = "system_accent"
    }

    object WallpaperPrimary : ColorOption() {
        override val isSupported = Utilities.ATLEAST_O_MR1

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.wallpaper) },
            {
                val context = LocalContext.current
                val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
                val primaryColor = wallpaperManager.wallpaperColors?.primaryColor
                primaryColor ?: LawnchairBlue.color
            }
        )

        override fun toString() = "wallpaper_primary"
    }

    class CustomColor(val color: Int) : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.custom) },
            { color }
        )

        constructor(color: Long) : this(color.toInt())

        override fun equals(other: Any?) = other is CustomColor && other.color == color

        override fun hashCode() = color

        override fun toString() = "custom|#${String.format("%08x", color)}"
    }

    companion object {
        val LawnchairBlue = CustomColor(0xFF007FFF)

        fun fromString(stringValue: String) = when (stringValue) {
            "system_accent" -> SystemAccent
            "wallpaper_primary" -> WallpaperPrimary
            else -> instantiateCustomColor(stringValue)
        }

        private fun instantiateCustomColor(stringValue: String): ColorOption {
            try {
                if (stringValue.startsWith("custom")) {
                    val color = Color.parseColor(stringValue.substring(7))
                    return CustomColor(color)
                }
            } catch (e: IllegalArgumentException) {
                // ignore
            }
            return SystemAccent
        }
    }
}
