package ch.deletescape.lawnchair.preferences

import android.content.Context
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.config.FeatureFlags

open class ThemerImpl : IThemer{
    override fun allAppsBackgroundColor(context: Context): Int {
        return Utilities
                .resolveAttributeData(FeatureFlags.applyDarkTheme(context, FeatureFlags.DARK_ALLAPPS), R.attr.allAppsContainerColor);
    }

    override fun allAppsBackgroundColorBlur(context: Context): Int {
        return Utilities
                .resolveAttributeData(FeatureFlags.applyDarkTheme(context, FeatureFlags.DARK_BLUR), R.attr.allAppsContainerColorBlur);
    }

}