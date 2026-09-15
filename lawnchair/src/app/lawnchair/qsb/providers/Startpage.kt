package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Startpage — privacy search. Website-only hotseat QSB provider. */
data object Startpage : QsbSearchProvider(
    id = "startpage",
    name = R.string.search_provider_startpage,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.startpage.com/sp/search?query=",
    type = QsbSearchProviderType.WEBSITE,
)
