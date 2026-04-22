package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

/**
 * Unified Kagi search provider that attempts to launch the Kagi mobile app first,
 * and falls back to opening the Kagi website if the app is not installed.
 */
data object Kagi : QsbSearchProvider(
    id = "kagi",
    name = R.string.search_provider_kagi,
    icon = R.drawable.ic_kagi,
    themedIcon = R.drawable.ic_kagi_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.kagi.search",
    className = "com.kagi.search.HomeActivity",
    action = "WIDGET_SEARCH_TEXT",
    website = "https://kagi.com",
    type = QsbSearchProviderType.APP_AND_WEBSITE,
    sponsored = false,
)
