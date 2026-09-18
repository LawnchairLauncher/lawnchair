package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Stack Overflow search — website-only hotseat QSB provider. */
data object StackOverflow : QsbSearchProvider(
    id = "stackoverflow",
    name = R.string.search_provider_stackoverflow,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://stackoverflow.com/search?q=",
    type = QsbSearchProviderType.WEBSITE,
)
