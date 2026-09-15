package app.lawnchair.qsb.providers

import com.android.launcher3.R

/**
 * Qwant — privacy-focused European search engine.
 * Website-only (no dedicated Android app package used here).
 */
data object Qwant : QsbSearchProvider(
    id = "qwant",
    name = R.string.search_provider_qwant,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.qwant.com/?q=",
    type = QsbSearchProviderType.WEBSITE,
)
