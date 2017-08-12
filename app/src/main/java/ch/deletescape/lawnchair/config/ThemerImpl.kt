package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.graphics.Color
import android.support.v4.content.ContextCompat
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.config.FeatureFlags
import ch.deletescape.lawnchair.dynamicui.ExtractedColors

open class ThemerImpl : IThemer {
    override fun allAppsIconTextLines(context: Context): Int {
        // TODO: currently only 1 is supported, higher values result in cut off lables
        return 1
    }

    override fun allAppsSearchTextColor(context: Context): Int? {
        return null
    }

    override fun allAppsSearchBarHintTextColor(context: Context): Int {
        return Utilities.getDynamicAccent(context)
    }

    override fun allAppsFastScrollerPopupTintColor(context: Context): Int? {
        if (Utilities.getPrefs(context).enableDynamicUi) {
            val tint = Utilities.getDynamicAccent(context)
            if (tint != -1) {
                return tint
            }
        }
        return null
    }

    override fun allAppsFastScrollerPopupTextColor(context: Context): Int {
        var color = Color.WHITE
        if (Utilities.getPrefs(context).enableDynamicUi) {
            val tint = Utilities.getDynamicAccent(context)
            if (tint != -1) {
                color = Utilities.getColor(context, ExtractedColors.VIBRANT_FOREGROUND_INDEX, Color.WHITE)
            }
        }
        return color
    }

    override fun allAppsIconTextColor(context: Context, allAppsAlpha: Int): Int {
        if (Utilities.getPrefs(context).useCustomAllAppsTextColor) {
            return Utilities.getLabelColor(context)
        } else if (FeatureFlags.useDarkTheme(FeatureFlags.DARK_ALLAPPS)) {
            return Color.WHITE
        } else if (allAppsAlpha < 128 && !BlurWallpaperProvider.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS) || allAppsAlpha < 50) {
            return Color.WHITE
        } else {
            return ContextCompat.getColor(context, R.color.quantum_panel_text_color)
        }
    }

    override fun allAppsFastScrollerHandleColor(context: Context): Int {
        return Utilities.getDynamicAccent(context)
    }

    override fun allAppsBackgroundColor(context: Context): Int {
        return Utilities
                .resolveAttributeData(FeatureFlags.applyDarkTheme(context, FeatureFlags.DARK_ALLAPPS), R.attr.allAppsContainerColor)
    }

    override fun allAppsBackgroundColorBlur(context: Context): Int {
        return Utilities
                .resolveAttributeData(FeatureFlags.applyDarkTheme(context, FeatureFlags.DARK_BLUR), R.attr.allAppsContainerColorBlur)
    }

}