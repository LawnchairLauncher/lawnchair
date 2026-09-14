package app.lawnchair.qsb.providers

import com.android.launcher3.R

/**
 * MetaGer — privacy-respecting meta-search (SUMA-EV, Germany).
 * Website-only hotseat QSB provider.
 */
data object MetaGer : QsbSearchProvider(
    id = "metager",
    name = R.string.search_provider_metager,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://metager.org/meta/meta.ger3?eingabe=",
    type = QsbSearchProviderType.WEBSITE,
)
