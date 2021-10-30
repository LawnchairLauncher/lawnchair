package app.lawnchair.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.util.SparseArray
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.SystemColorScheme
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.cam.Zcam
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.data.Illuminants
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs.Companion.toAbs
import dev.kdrag0n.colorkt.ucs.lab.CieLab
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.DynamicColorScheme
import dev.kdrag0n.monet.theme.GrayColorScheme
import dev.kdrag0n.monet.theme.MaterialYouTargets

class ThemeProvider(private val context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    private val accentColor by prefs.accentColor

    private val targets = MaterialYouTargets(1.0, false, viewingCondition)
    private val colorSchemeMap = SparseArray<ColorScheme>()
    private val listeners = mutableListOf<ColorSchemeChangeListener>()

    init {
        if (Utilities.ATLEAST_S) {
            colorSchemeMap.append(0, SystemColorScheme(context))
            registerOverlayChangedListener()
        }
        wallpaperManager.addOnChangeListener(object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                if (accentColor is ColorOption.WallpaperPrimary) {
                    notifyColorSchemeChanged()
                }
            }
        })
        prefs.accentColor.addListener(object : PreferenceChangeListener {
            override fun onPreferenceChange() {
                notifyColorSchemeChanged()
            }
        })
    }

    private fun registerOverlayChangedListener() {
        val packageFilter = IntentFilter("android.intent.action.OVERLAY_CHANGED")
        packageFilter.addDataScheme("package")
        packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    colorSchemeMap.append(0, SystemColorScheme(context))
                    if (accentColor is ColorOption.SystemAccent) {
                        notifyColorSchemeChanged()
                    }
                }
            },
            packageFilter,
            null,
            Handler(Looper.getMainLooper())
        )
    }

    val colorScheme get() = when (val accentColor = this.accentColor) {
        is ColorOption.SystemAccent -> systemColorScheme
        is ColorOption.WallpaperPrimary -> {
            val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
            getColorScheme(wallpaperPrimary ?: ColorOption.LawnchairBlue.color)
        }
        is ColorOption.CustomColor -> getColorScheme(accentColor.color)
    }

    private val systemColorScheme get() = when {
        Utilities.ATLEAST_S -> getColorScheme(0)
        else -> getColorScheme(context.getSystemAccent(darkTheme = false))
    }

    private fun getColorScheme(colorInt: Int): ColorScheme {
        var colorScheme = colorSchemeMap[colorInt]
        if (colorScheme == null) {
            val color = Srgb(colorInt)
            colorScheme = DynamicColorScheme(targets, color, 1.0, viewingCondition, true)
            colorSchemeMap.append(colorInt, colorScheme)
        }
        return colorScheme
    }

    fun addListener(listener: ColorSchemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ColorSchemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyColorSchemeChanged() {
        ArrayList(listeners)
            .forEach(ColorSchemeChangeListener::onColorSchemeChanged)
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::ThemeProvider)

        val viewingCondition = Zcam.ViewingConditions(
            Zcam.ViewingConditions.SURROUND_AVERAGE,
            0.4f * CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE,
            CieLab(50.0, 0.0, 0.0, Illuminants.D65).toXyz().y * CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE,
            Illuminants.D65.toAbs(CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE)
        )
    }

    interface ColorSchemeChangeListener {
        fun onColorSchemeChanged()
    }
}

fun Color.toAndroidColor(): Int {
    return when (this) {
        is AndroidColor -> color
        is Srgb -> ColorUtils.setAlphaComponent(toRgb8(), 255)
        else -> convert<Srgb>().toAndroidColor()
    }
}
