package app.lawnchair.qsb.providers

import com.android.launcher3.R

/**
 * Swisscows — privacy-focused search (Switzerland).
 * Website-only hotseat QSB provider.
 */
data object Swisscows : QsbSearchProvider(
    id = "swisscows",
    name = R.string.search_provider_swisscows,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://swisscows.com/en/web?query=",
    type = QsbSearchProviderType.WEBSITE,
)
