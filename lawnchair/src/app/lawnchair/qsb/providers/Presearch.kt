package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Presearch — privacy-focused metasearch (website-only). */
data object Presearch : QsbSearchProvider(
    id = "presearch",
    name = R.string.search_provider_presearch,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://presearch.com/search",
    type = QsbSearchProviderType.WEBSITE,
)
