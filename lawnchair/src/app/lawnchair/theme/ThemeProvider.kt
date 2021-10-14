package app.lawnchair.theme

import android.content.Context
import android.util.SparseArray
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.SystemColorScheme
import app.lawnchair.ui.theme.getSystemAccent
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
import dev.kdrag0n.monet.theme.MaterialYouTargets

class ThemeProvider(private val context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private val useSystemAccent by prefs.useSystemAccent
    private val customAccentColor by prefs.accentColor

    private val targets = MaterialYouTargets(1.0, false, viewingCondition)
    private val colorSchemeMap = SparseArray<ColorScheme>()

    init {
        if (Utilities.ATLEAST_S) {
            colorSchemeMap.append(0, SystemColorScheme(context))
        }
    }

    val colorScheme get() = when {
        useSystemAccent -> systemColorScheme
        else -> getColorScheme(customAccentColor)
    }

    val systemColorScheme get() = when {
        Utilities.ATLEAST_S -> getColorScheme(0)
        else -> getColorScheme(context.getSystemAccent(darkTheme = false))
    }

    fun getColorScheme(colorInt: Int): ColorScheme {
        var colorScheme = colorSchemeMap[colorInt]
        if (colorScheme == null) {
            val color = Srgb(colorInt)
            colorScheme = DynamicColorScheme(targets, color, 1.0, viewingCondition, true)
            colorSchemeMap.append(colorInt, colorScheme)
        }
        return colorScheme
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
}

fun Color.toAndroidColor(): Int {
    return when (this) {
        is AndroidColor -> color
        is Srgb -> ColorUtils.setAlphaComponent(toRgb8(), 255)
        else -> convert<Srgb>().toAndroidColor()
    }
}
