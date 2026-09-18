package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Mojeek — independent privacy-focused search (website-only). */
data object Mojeek : QsbSearchProvider(
    id = "mojeek",
    name = R.string.search_provider_mojeek,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.mojeek.com/",
    type = QsbSearchProviderType.WEBSITE,
)
