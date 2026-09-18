package app.lawnchair.qsb.providers

import com.android.launcher3.R

/**
 * Brave Search website (independent from the Brave browser app provider).
 * Website-only hotseat QSB provider.
 */
data object BraveSearch : QsbSearchProvider(
    id = "brave_search",
    name = R.string.search_provider_brave_search,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://search.brave.com/search?q=",
    type = QsbSearchProviderType.WEBSITE,
)
