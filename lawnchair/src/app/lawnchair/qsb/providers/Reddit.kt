package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Reddit search — website-only hotseat QSB provider. */
data object Reddit : QsbSearchProvider(
    id = "reddit",
    name = R.string.search_provider_reddit,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.reddit.com/search/?q=",
    type = QsbSearchProviderType.WEBSITE,
)
