package ch.deletescape.lawnchair.colors

import android.graphics.Color
import android.support.annotation.Keep
import android.view.ContextThemeWrapper
import ch.deletescape.lawnchair.getColorAccent
import com.android.launcher3.R
import com.android.launcher3.uioverrides.WallpaperColorInfo

@Keep
class SystemAccentResolver(config: Config) : ColorEngine.ColorResolver(config) {

    private val accentColor = ContextThemeWrapper(engine.context, android.R.style.Theme_DeviceDefault).getColorAccent()

    override fun resolveColor() = accentColor

    override fun getDisplayName() = engine.context.getString(R.string.color_system_accent) as String
}

@Keep
class PixelAccentResolver(config: Config) : ColorEngine.ColorResolver(config) {

    private val accentColor = ContextThemeWrapper(engine.context, R.style.BaseLauncherThemeWithCustomAttrs).getColorAccent()

    override fun resolveColor() = accentColor

    override fun getDisplayName() = engine.context.getString(R.string.color_pixel_accent) as String
}

@Keep
class RGBColorResolver(config: Config) : ColorEngine.ColorResolver(config) {

    val color: Int

    init {
        if (args.size < 3) throw IllegalArgumentException("not enough args")
        val rgb = args.subList(0, 3).map { it.toIntOrNull() ?: throw IllegalArgumentException("args malformed: $it") }
        color = Color.rgb(rgb[0], rgb[1], rgb[2])
    }

    override fun resolveColor() = color

    override fun getDisplayName() = "#${String.format("%06X", color and 0xFFFFFF)}"
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

    override fun resolveColor() = colorInfo.mainColor

    override fun getDisplayName() = engine.context.getString(R.string.color_wallpaper_main) as String
}


@Keep
class WallpaperSecondaryColorResolver(config: Config) : WallpaperColorResolver(config) {

    override fun resolveColor() = colorInfo.secondaryColor

    override fun getDisplayName() = engine.context.getString(R.string.color_wallpaper_secondary) as String
}
