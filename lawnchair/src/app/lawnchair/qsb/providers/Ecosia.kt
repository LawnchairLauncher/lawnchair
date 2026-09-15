package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Ecosia — search that plants trees. Website-only hotseat QSB provider. */
data object Ecosia : QsbSearchProvider(
    id = "ecosia",
    name = R.string.search_provider_ecosia,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.ecosia.org/search?q=",
    type = QsbSearchProviderType.WEBSITE,
)
