package app.lawnchair.qsb.providers

import com.android.launcher3.R

/**
 * You.com web search provider for the hotseat QSB.
 * Website-only (no dedicated Android app); opens https://you.com/search.
 */
data object YouCom : QsbSearchProvider(
    id = "youcom",
    name = R.string.search_provider_youcom,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://you.com/search",
    type = QsbSearchProviderType.WEBSITE,
)
