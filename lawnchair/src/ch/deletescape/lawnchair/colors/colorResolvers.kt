/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.colors

import android.graphics.Color
import android.support.annotation.Keep
import android.text.TextUtils
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo

@Keep
class LawnchairAccentResolver(config: Config) : ColorEngine.ColorResolver(config), ColorEngine.OnColorChangeListener {

    override fun startListening() {
        super.startListening()
        engine.addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        engine.removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun resolveColor() = engine.accent

    override fun getDisplayName() = engine.context.getString(R.string.lawnchair_accent) as String
}

@Keep
class SystemAccentResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override val themeAware = true
    override val themeSet = ThemeOverride.DeviceDefault()

    override fun resolveColor(): Int {
        var color = themedContext.getColorAttr(android.R.attr.colorAccent)
        // Oxygen OS accent color, also used by some custom ROMs now
        var propertyValue = Utilities.getSystemProperty("persist.sys.theme.accentcolor", "")
        if (!TextUtils.isEmpty(propertyValue)) {
            if (!propertyValue.startsWith('#')) propertyValue = "#$propertyValue"
            try {
                color = Color.parseColor(propertyValue)
            } catch (e: IllegalArgumentException) {
            }
        }
        return color
    }

    override fun getDisplayName() = engine.context.getString(R.string.color_system_accent) as String
}

@Keep
class PixelAccentResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override val themeAware = true

    override fun resolveColor(): Int {
        return themedContext.getColorAttr(android.R.attr.colorAccent)
    }

    override fun getDisplayName() = engine.context.getString(R.string.color_pixel_accent) as String
}

@Keep
class RGBColorResolver(config: Config) : ColorEngine.ColorResolver(config) {

    val color: Int
    override val isCustom = true

    init {
        if (args.size < 3) throw IllegalArgumentException("not enough args")
        val rgb = args.subList(0, 3).map { it.toIntOrNull() ?: throw IllegalArgumentException("args malformed: $it") }
        color = Color.rgb(rgb[0], rgb[1], rgb[2])
    }

    override fun resolveColor() = color

    override fun getDisplayName() = "#${String.format("%06X", color and 0xFFFFFF)}"
}

@Keep
class ARGBColorResolver(config: Config) : ColorEngine.ColorResolver(config) {

    val color: Int
    override val isCustom = true

    init {
        if (args.size < 4) throw IllegalArgumentException("not enough args")
        val argb = args.subList(0, 4).map {
            it.toIntOrNull() ?: throw IllegalArgumentException("args malformed: $it")
        }
        color = Color.argb(argb[0], argb[1], argb[2], argb[3])
    }

    override fun resolveColor() = color

    override fun getDisplayName() = "#${String.format("%08X", color.toLong() and 0xFFFFFFFF)}"
}

abstract class WallpaperColorResolver(config: Config)
    : ColorEngine.ColorResolver(config), WallpaperColorInfo.OnChangeListener {

    protected val colorInfo = WallpaperColorInfo.getInstance(engine.context) as WallpaperColorInfo

    override fun startListening() {
        super.startListening()
        colorInfo.addOnChangeListener(this)
    }

    override fun stopListening() {
        super.stopListening()
        colorInfo.removeOnChangeListener(this)
    }

    override fun onExtractedColorsChanged(wallpaperColorInfo: WallpaperColorInfo) {
        notifyChanged()
    }
}

@Keep
class WallpaperMainColorResolver(config: Config) : WallpaperColorResolver(config) {

    override fun resolveColor() = colorInfo.actualMainColor

    override fun getDisplayName() = engine.context.getString(R.string.color_wallpaper_main) as String
}

@Keep
class WallpaperSecondaryColorResolver(config: Config) : WallpaperColorResolver(config) {

    override fun resolveColor() = colorInfo.actualSecondaryColor

    override fun getDisplayName() = engine.context.getString(R.string.color_wallpaper_secondary) as String
}

@Keep
class WallpaperTertiaryColorResolver(config: Config) : WallpaperColorResolver(config) {

    override fun resolveColor() = colorInfo.tertiaryColor

    override fun getDisplayName() = engine.context.getString(R.string.color_wallpaper_tertiary) as String
}

abstract class ThemeAttributeColorResolver(config: Config) :
        ColorEngine.ColorResolver(config) {

    protected abstract val colorAttr: Int
    override val themeAware = true

    override fun resolveColor(): Int {
        return themedContext.getColorAttr(colorAttr)
    }

    override fun getDisplayName() = context.getString(R.string.theme_based)
}
