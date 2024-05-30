package app.lawnchair.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.util.SparseArray
import androidx.compose.material3.ColorScheme
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.MonetTonalPaletteCompat
import app.lawnchair.theme.color.SystemTonalPalette
import app.lawnchair.theme.colorscheme.LightDarkScheme
import app.lawnchair.theme.utils.getLightDarkScheme
import app.lawnchair.theme.utils.getSystemLightDarkScheme
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.TonalPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ThemeProvider(private val context: Context) {
    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var accentColor: ColorOption = preferenceManager2.accentColor.firstBlocking()

    private val tonalPaletteMap = SparseArray<TonalPalette>()
    private val listeners = mutableListOf<TonalPaletteChangeListener>()

    init {
        if (Utilities.ATLEAST_S) {
            tonalPaletteMap.append(0, SystemTonalPalette(context))
            registerOverlayChangedListener()
        }
        wallpaperManager.addOnChangeListener(object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                if (accentColor is ColorOption.WallpaperPrimary) {
                    notifyTonalPaletteChanged()
                }
            }
        })
        preferenceManager2.accentColor.onEach(launchIn = coroutineScope) {
            accentColor = it
            notifyTonalPaletteChanged()
        }
    }

    private fun registerOverlayChangedListener() {
        val packageFilter = IntentFilter("android.intent.action.OVERLAY_CHANGED")
        packageFilter.addDataScheme("package")
        packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    tonalPaletteMap.append(0, SystemTonalPalette(context))
                    if (accentColor is ColorOption.SystemAccent) {
                        notifyTonalPaletteChanged()
                    }
                }
            },
            packageFilter,
            null,
            Handler(Looper.getMainLooper()),
        )
    }

    val colorScheme get() = when (val accentColor = this.accentColor) {
        is ColorOption.SystemAccent -> systemColorScheme
        is ColorOption.WallpaperPrimary -> {
            val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
            getLightDarkScheme(wallpaperPrimary ?: ColorOption.LawnchairBlue.color)
        }
        is ColorOption.CustomColor -> getLightDarkScheme(accentColor.color)
        else -> getLightDarkScheme(ColorOption.LawnchairBlue.color)
    }

    private val systemColorScheme get() = when {
        Utilities.ATLEAST_S -> getSystemLightDarkScheme(context)
        else -> getLightDarkScheme(context.getSystemAccent(darkTheme = false))
    }

    val tonalPalette get() = when (val accentColor = this.accentColor) {
        is ColorOption.SystemAccent -> systemTonalPalette
        is ColorOption.WallpaperPrimary -> {
            val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
            getTonalPalette(wallpaperPrimary ?: ColorOption.LawnchairBlue.color)
        }
        is ColorOption.CustomColor -> getTonalPalette(accentColor.color)
        else -> getTonalPalette(ColorOption.LawnchairBlue.color)
    }

    private val systemTonalPalette get() = when {
        Utilities.ATLEAST_S -> getTonalPalette(0) // Tonal palette is from line 41
        else -> getTonalPalette(context.getSystemAccent(darkTheme = false))
    }

    private fun getTonalPalette(colorInt: Int): TonalPalette {
        var tonalPalette = tonalPaletteMap[colorInt]
        if (tonalPalette == null) {
            tonalPalette = MonetTonalPaletteCompat(colorInt)
            tonalPaletteMap.append(colorInt, tonalPalette)
        }
        return tonalPalette
    }

    fun addListener(listener: TonalPaletteChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: TonalPaletteChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyTonalPaletteChanged() {
        ArrayList(listeners)
            .forEach(TonalPaletteChangeListener::onTonalPaletteChanged)
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::ThemeProvider)
    }

    sealed interface TonalPaletteChangeListener {
        fun onTonalPaletteChanged()
    }
}

fun Color.toAndroidColor(): Int {
    return when (this) {
        is AndroidColor -> color
        is Srgb -> ColorUtils.setAlphaComponent(toRgb8(), 255)
        else -> convert<Srgb>().toAndroidColor()
    }
}
