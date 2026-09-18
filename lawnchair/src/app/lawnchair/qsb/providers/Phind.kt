package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Phind — AI search for developers. Website-only hotseat QSB provider. */
data object Phind : QsbSearchProvider(
    id = "phind",
    name = R.string.search_provider_phind,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.phind.com/search?q=",
    type = QsbSearchProviderType.WEBSITE,
)
