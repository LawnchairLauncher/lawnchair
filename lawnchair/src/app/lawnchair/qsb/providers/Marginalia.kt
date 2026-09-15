package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Marginalia — independent indie web search. Website-only hotseat QSB provider. */
data object Marginalia : QsbSearchProvider(
    id = "marginalia",
    name = R.string.search_provider_marginalia,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://search.marginalia.nu/search?query=",
    type = QsbSearchProviderType.WEBSITE,
)
