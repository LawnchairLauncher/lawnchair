package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Yahoo Search — website-only hotseat QSB provider. */
data object Yahoo : QsbSearchProvider(
    id = "yahoo",
    name = R.string.search_provider_yahoo,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://search.yahoo.com/search?p=",
    type = QsbSearchProviderType.WEBSITE,
)
